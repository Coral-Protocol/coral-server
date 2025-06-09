package org.coralprotocol.coralserver.gaia

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.coralserver.config.AppConfig
import org.coralprotocol.coralserver.orchestrator.AgentRegistry
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.ConfigEntry
import org.coralprotocol.coralserver.orchestrator.Orchestrator
import org.coralprotocol.coralserver.orchestrator.RegistryAgent
import org.coralprotocol.coralserver.orchestrator.runtime.Executable
import org.coralprotocol.coralserver.orchestrator.runtime.executable.EnvVar
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

val serverPort: UShort = 5555u

val assistantAgent = AgentType("assistant")
val imageAgent = AgentType("image")
val planningAgent = AgentType("planning")
val searchAgent = AgentType("search")
val videoAgent = AgentType("video")
val webAgent = AgentType("web")
val answerFindingAgent = AgentType("answer_finding")
val commonRegistryOptionsList = listOf(
    ConfigEntry.Str("OPENAI_API_KEY", "OpenAI API Key", null),
    ConfigEntry.Str("GOOGLE_API_KEY", "Google API Key", null),
    ConfigEntry.Str("SEARCH_ENGINE_ID", "Search Engine ID", null),
    ConfigEntry.Str("TASK_INSTRUCTION", "The task to instruct the", null),
    ConfigEntry.Str("TASK_ID", "The gaia question ID", null),
)
val openAiApiKey: String = System.getenv("OPENAI_API_KEY")
val googleApiKey: String = System.getenv("GOOGLE_API_KEY")
val searchEngineId: String = System.getenv("SEARCH_ENGINE_ID")

val commonRegistryEnvList = listOf(
    EnvVar(
        "OPENAI_API_KEY",
        from = "OPENAI_API_KEY",
        value = openAiApiKey,
        option = "OPENAI_API_KEY"
    ),
    EnvVar(
        "GOOGLE_API_KEY",
        from = "GOOGLE_API_KEY",
        value = googleApiKey,
        option = "GOOGLE_API_KEY"
    ),
    EnvVar(
        "SEARCH_ENGINE_ID",
        from = "SEARCH_ENGINE_ID",
        value = searchEngineId,
        option = "SEARCH_ENGINE_ID"
    ),
    EnvVar(
        "TASK_INSTRUCTION",
        from = "TASK_INSTRUCTION",
        option = "TASK_INSTRUCTION"
    ),
    EnvVar(
        "TASK_ID",
        from = "TASK_ID",
        option = "TASK_ID"
    )
)


@Serializable
data class GaiaAnswerAttempt(
    val questionId: String,
    val answer: String,
    val sessionId: String,
) {
//    val correctAnswer: String
//        get() = question.finalAnswer
}


/**
 * Application that runs the whole GAIA benchmark.
 *
 * A server is started for the answering agent to send answers to via HTTP.
 * (It is expected to do this through tool calls)
 */
class GaiaApplication(val server: CoralServer) {

    val client = HttpClient() {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
    }

    // Channels for receiving Gaia answers
    val answerChannels: ConcurrentMap<GaiaQuestionId, Flow<GaiaAnswerAttempt>> = ConcurrentHashMap()
    private val embeddedAnswerServer =
        embeddedServer(CIO, host = server.host, port = 12081, watchPaths = listOf("classes")) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post("answers") {
                    val answerAttempt = call.receive<GaiaAnswerAttempt>()
                    val questionId = answerAttempt.questionId

                    // end the session if it exists
                    server.sessionManager.getSession(questionId)?.coralAgentConnections?.forEach {
                        it.closeTransport()
                        println("Closed transport for session: $questionId for agent: ${it.connectedAgentId}")
                    }
                    // Store the answer attempt in the channel
                    val channel = answerChannels.computeIfAbsent(questionId) {
                        MutableSharedFlow(extraBufferCapacity = 1)
                    }
                    (channel as MutableSharedFlow<GaiaAnswerAttempt>).emit(answerAttempt)

                    // Respond with OK status
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    private var isStarted = false

    fun startAnswerServer(wait: Boolean = true) {
        isStarted = true
        embeddedAnswerServer.start(wait = wait)
    }

    val waitingForAnswerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    suspend fun findAnswer(question: GaiaQuestion): Deferred<GaiaAnswerAttempt> {
        if (!isStarted) {
            throw IllegalStateException("Server is not started. Call start() before finding an answer.")
        }

        val address = "http://localhost:${server.port}"
        val sessionPostResponse = client.post("$address/sessions") {
            contentType(ContentType.Application.Json)
            setBody(
                creationSessionRequest(question)
            )
        }

        // Ensure there is a flow
        val answerChannel = answerChannels.computeIfAbsent(question.taskId) {
            MutableSharedFlow(extraBufferCapacity = 1)
        }

        if (sessionPostResponse.status != HttpStatusCode.OK) {
            throw IllegalStateException("Failed to create session: ${sessionPostResponse.status}")
        }
        val completableDeferred = CompletableDeferred<GaiaAnswerAttempt>()


        waitingForAnswerScope.launch {
            answerChannel.collect { answerAttempt ->
                if (answerAttempt.questionId != question.taskId) {
                    throw IllegalStateException("Received answer for a different question: ${answerAttempt.questionId} != ${question.taskId}")
                }

                completableDeferred.complete(answerAttempt)
            }
        }

        return completableDeferred
    }


    private fun creationSessionRequest(question: GaiaQuestion): CreateSessionRequest {

        val questionWithFile = if (question.file != null) {
            "${question.question}\n relevant file: ${question.file.absolutePath}"
        } else {
            question.question
        }
        val commonOptions = mapOf(
            "OPENAI_API_KEY" to JsonPrimitive(openAiApiKey),
            "GOOGLE_API_KEY" to JsonPrimitive(googleApiKey),
            "SEARCH_ENGINE_ID" to JsonPrimitive(searchEngineId),
            "TASK_INSTRUCTION" to JsonPrimitive(questionWithFile),
            "TASK_ID" to JsonPrimitive(question.taskId)
        )
        return CreateSessionRequest(
            "gaia", "gaia-1", "public", AgentGraphRequest(
                agents = hashMapOf(
                    getAgentInstanceReference(commonOptions, "search", searchAgent),
                    getAgentInstanceReference(commonOptions, "planning", planningAgent),
                    getAgentInstanceReference(commonOptions, "assistant", assistantAgent),
                    getAgentInstanceReference(commonOptions, "image", imageAgent),
                    getAgentInstanceReference(commonOptions, "video", videoAgent),
                    getAgentInstanceReference(commonOptions, "web", webAgent),
                    getAgentInstanceReference(commonOptions, "answer_finding", answerFindingAgent)
                ),
                links = setOf(setOf("search", "planning", "assistant", "image", "video", "web", "answer_finding"))
            )
        )
    }

    private fun getAgentInstanceReference(
        commonOptions: Map<String, JsonPrimitive>, name: String, agentType: AgentType, blocking: Boolean = true
    ): Pair<AgentName, GraphAgentRequest.Local> =
        AgentName(name) to GraphAgentRequest.Local(
            agentType,
            blocking = blocking,
            options = commonOptions
        )
}


fun createServerWithRegisteredAgents(): CoralServer {
    fun registerGaiaAgent(agentPath: String): RegistryAgent = RegistryAgent(
        Executable(
            listOf("bash", "coral-GAIA/venv.sh", agentPath),
            commonRegistryEnvList
        ),
        optionsList = commonRegistryOptionsList
    )

    val registry = AgentRegistry(
        mapOf(
            searchAgent to registerGaiaAgent("coral-GAIA/agents/search_agent.py"),
            planningAgent to registerGaiaAgent("coral-GAIA/agents/planning_agent.py"),
            assistantAgent to registerGaiaAgent("coral-GAIA/agents/assistant_agent.py"),
            imageAgent to registerGaiaAgent("coral-GAIA/agents/image_agent.py"),
            videoAgent to registerGaiaAgent("coral-GAIA/agents/video_agent.py"),
            webAgent to registerGaiaAgent("coral-GAIA/agents/web_agent.py"),
            answerFindingAgent to registerGaiaAgent("coral-GAIA/agents/answer_finding_agent.py")
        )
    )
    val orchestrator = Orchestrator(registry)

    val coralServer = CoralServer(
        devmode = false,
        sessionManager = SessionManager(
            orchestrator,
            serverPort,
        ),
        appConfig = AppConfig(
            registry = registry.agents
        )
    )

    return coralServer
}

suspend fun main(args: Array<String>) {

    val server = createServerWithRegisteredAgents()
    val questions = loadGaiaQuestions(MetadataFiles.validationMetadata)
    GaiaApplication(server).apply {
        server.start()
        startAnswerServer(wait = false)

        var correctAnswersCount = 0
        var allAnswersCount = 0
        val questionAnswerPairs = mutableListOf<Pair<GaiaQuestion, Deferred<GaiaAnswerAttempt>>>()
        questions.forEach { question ->
            try {
                withTimeout(300 * 1000) {
                    val answerDeferred = findAnswer(question)
                    println("Waiting for answer to question: ${question.question}")
                    val answerAttempt = answerDeferred.await()
                    println("Received answer attempt: $answerAttempt")
                    if (question.finalAnswer != answerAttempt.answer) {
                        println("The answer attempt is incorrect! Expected: ${question.finalAnswer}, got: ${answerAttempt.answer}")
                    } else {
                        println("The answer attempt is correct!")
                        correctAnswersCount++
                    }
                    questionAnswerPairs.add(question to answerDeferred)
                }
            } catch (e: TimeoutCancellationException) {
                println("Timeout while waiting for answer to question: ${question.question}")
                e.printStackTrace()
            } catch (e: Exception) {
                println("Error while waiting for answer to question: ${question.question}")
                e.printStackTrace()
            }

            allAnswersCount++
            println("So far, correct answers: $correctAnswersCount, all answers: $allAnswersCount, total questions: ${questions.size}")
        }

        val json = Json.encodeToString(questionAnswerPairs)
        File("coral-GAIA/answers.json").writeText(json)
        server.stop()
    }
}
