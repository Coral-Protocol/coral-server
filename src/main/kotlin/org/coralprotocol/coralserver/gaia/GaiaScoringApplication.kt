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
    ConfigEntry.Str("TASK_INSTRUCTION", "The task to instruct the", null),
    ConfigEntry.Str("TASK_ID", "The gaia question ID", null),
)
val openAiApiKey: String = System.getenv("OPENAI_API_KEY")

val commonRegistryEnvList = listOf(
    EnvVar(
        "OPENAI_API_KEY",
        from = "OPENAI_API_KEY",
        value = openAiApiKey,
        option = "OPENAI_API_KEY"
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


data class GaiaAnswerAttempt(
    val questionId: GaiaQuestionId,
    val answer: String,
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
        val commonOptions = mapOf("OPENAI_API_KEY" to JsonPrimitive(openAiApiKey),
            "TASK_INSTRUCTION" to JsonPrimitive(question.question),
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
    val questions = loadGaiaQuestions(MetadataFiles.testMetadata)
    GaiaApplication(server).apply {
        server.start()
        startAnswerServer(wait = false)
        val firstAnswer = findAnswer(questions.first())
        println("Waiting for answer to question: ${questions.first().question}")
        val answerAttempt = firstAnswer.await()
        println("Received answer attempt: $answerAttempt")

        server.stop()
    }
}