package org.coralprotocol.coralserver.gaia

import io.ktor.client.*
import io.ktor.client.call.body
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.coralserver.config.AppConfig
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.config.ApplicationConfig
import org.coralprotocol.coralserver.config.custom
import org.coralprotocol.coralserver.models.ResolvedThread
import org.coralprotocol.coralserver.models.resolve
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
val problemSolvingAgent = AgentType("problem_solving")

@Serializable
data class GaiaAnswerAttempt(
    val questionId: String,
    val answer: String,
    val sessionId: String,
    val justification: String
) {
    @Transient
    var session: CoralAgentGraphSession? = null
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

                    answerAttempt.session = server.sessionManager.getSession(answerAttempt.sessionId)
                        ?: throw IllegalStateException("Session not found for answer attempt: ${answerAttempt.sessionId}")
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
        val responseBody = sessionPostResponse.body<CreateSessionResponse>()
        val completableDeferred = CompletableDeferred<GaiaAnswerAttempt>()


        waitingForAnswerScope.launch {
            launch {
                delay(10 * 60 * 1000)
                try {
                    endSession(responseBody)
                } catch (e: Exception) {
                    println("Failed to end session after timeout: ${e.message}")
                }
            }
            answerChannel.collect { answerAttempt ->
                if (answerAttempt.questionId != question.taskId) {
                    throw IllegalStateException("Received answer for a different question: ${answerAttempt.questionId} != ${question.taskId}")
                }
                completableDeferred.complete(answerAttempt)
                endSession(responseBody)
            }
        }

        return completableDeferred
    }


    /**
     * Helper function to create common options map for agent configuration
     */
    private fun createCommonOptionsMap(
        question: GaiaQuestion,
        customValues: Map<String, String> = emptyMap()
    ): Map<String, JsonPrimitive> {
        val questionWithFile = if (question.file != null) {
            buildString {
                append("${question.question}\n relevant file: ${question.file.absolutePath}\n")
                append("Note that even if you don't have tools to work with files, others in your team will. Make sure you're all working around this file.")
            }
        } else {
            question.question
        }

        // Start with the base environment variables
        val optionsMap = EnvVars.definitions
            .filter { it.isApiKey }
            .associate { it.name to JsonPrimitive(customValues[it.name] ?: EnvVars.getValue(it.name)) }
            .toMutableMap()

        // Add task-specific variables
        optionsMap["TASK_INSTRUCTION"] = JsonPrimitive(questionWithFile)
        optionsMap["TASK_ID"] = JsonPrimitive(question.taskId)
        optionsMap["AGENT_WORKING_DIRECTORY"] =
            JsonPrimitive(GaiaConfig.getOrCreateSessionWorkingDirectory(question.taskId).absolutePath)

        return optionsMap
    }

    /**
     * Helper function to create agent instance reference
     */
    private fun getAgentInstanceReference(
        commonOptions: Map<String, JsonPrimitive>,
        name: String,
        agentType: AgentType,
        blocking: Boolean = true
    ): Pair<AgentName, GraphAgentRequest.Local> =
        AgentName(name) to GraphAgentRequest.Local(
            agentType,
            blocking = blocking,
            options = commonOptions
        )

    private fun creationSessionRequest(question: GaiaQuestion): CreateSessionRequest {
        val commonOptions = createCommonOptionsMap(question)

        // Create agent instances using the helper method
        return CreateSessionRequest(
            "gaia", "gaia-1", "public", AgentGraphRequest(
                agents = hashMapOf(
                    getAgentInstanceReference(commonOptions, "search", searchAgent),
                    getAgentInstanceReference(commonOptions, "planning", planningAgent),
                    getAgentInstanceReference(commonOptions, "assistant", assistantAgent),
                    getAgentInstanceReference(commonOptions, "image", imageAgent),
                    getAgentInstanceReference(commonOptions, "video", videoAgent),
                    getAgentInstanceReference(commonOptions, "web", webAgent),
                    getAgentInstanceReference(commonOptions, "answer_finding", answerFindingAgent),
                    getAgentInstanceReference(commonOptions, "problem_solving", problemSolvingAgent)
                ),
                links = setOf(
                    setOf(
                        "search", "planning", "assistant", "image", "video", "web", "answer_finding",
                        "problem_solving"
                    )
                )
            )
        )
    }
}

private suspend fun GaiaApplication.endSession(responseBody: CreateSessionResponse) {
    println("Ending session with ID: ${responseBody.sessionId}")
    // Wait for the answer attempt to be emitted
    server.sessionManager.getSession(responseBody.sessionId)?.let { session ->
        server.sessionManager.orchestrator.destroy(session.id)
    } ?: throw IllegalStateException("Session not found: ${responseBody.sessionId}")

}


/**
 * Helper function to create a registry agent for GAIA
 */
fun createRegistryAgent(
    agentPath: String,
    envList: List<EnvVar> = EnvVars.envVars,
    options: List<ConfigEntry> = EnvVars.configEntries
): RegistryAgent = RegistryAgent(
    Executable(
        listOf("bash", File(GaiaConfig.multiAgentSystemRootDir, "venv.sh").absolutePath, agentPath),
        envList
    ),
    optionsList = options
)

/**
 * Helper function to create an agent registry entry
 */
fun createAgentRegistryEntry(
    agentType: AgentType,
    agentName: String,
    absoluteBasePath: String = GaiaConfig.multiAgentSystemRootDir.absolutePath
): Pair<AgentType, RegistryAgent> =
    agentType to createRegistryAgent("$absoluteBasePath/agents/${agentName}_agent.py")

fun createServerWithRegisteredAgents(): CoralServer {
    val registry = AgentRegistry(
        mapOf(
            createAgentRegistryEntry(searchAgent, "search"),
            createAgentRegistryEntry(planningAgent, "planning"),
            createAgentRegistryEntry(assistantAgent, "assistant"),
            createAgentRegistryEntry(imageAgent, "image"),
            createAgentRegistryEntry(videoAgent, "video"),
            createAgentRegistryEntry(webAgent, "web"),
            createAgentRegistryEntry(answerFindingAgent, "answer_finding"),
            createAgentRegistryEntry(problemSolvingAgent, "problem_solving"),
        )
    )
    val appConfigLoader = AppConfigLoader.custom(
        AppConfig(
            registry = registry,
            applications = listOf(ApplicationConfig("gaia", "gaia", "gaia application", listOf("public")))
        )
    ).apply { stopWatch() }
    val orchestrator = Orchestrator(appConfigLoader)
    val coralServer = CoralServer(
        devmode = false,
        sessionManager = SessionManager(
            orchestrator,
            serverPort,
        ),
        appConfig = appConfigLoader
    )

    return coralServer
}

@Serializable
data class GaiaResult(
    val question: GaiaQuestion,
    val answerAttempt: GaiaAnswerAttempt,
    val isCorrect: Boolean = question.finalAnswer.lowercase() == answerAttempt.answer.lowercase(),
    val threads: List<ResolvedThread>? = null
)

fun saveResultToFile(result: GaiaResult) {
    println("Saving result for question: ${result.question}, session: ${result.answerAttempt.sessionId}")
    println("Answer attempt (correct: ${result.isCorrect}): ${result.answerAttempt.answer}")
    println("Justification: ${result.answerAttempt.justification}")
    val questionDir = File(GaiaConfig.gaiaQuestionSet.resultsDir, result.question.taskId)
    val correctnessDir = File(questionDir, if (result.isCorrect) "correct" else "incorrect")
    val resultHash = result.answerAttempt.answer.hashCode().toString(16)
    val resultFile = File(correctnessDir, "$resultHash.json")
    if (!correctnessDir.exists()) {
        correctnessDir.mkdirs()
    }
    resultFile.writeText(Json.encodeToString(result))
}

fun loadAllGaiaResults(): Set<GaiaResult> {
    val resultsDir = GaiaConfig.gaiaQuestionSet.resultsDir
    if (!resultsDir.exists()) {
        return emptySet()
    }

    return resultsDir.walk().filter { it.isFile && it.extension == "json" }.map { file ->
        val content = file.readText()
        Json.decodeFromString<GaiaResult>(content)
    }.toSet()
}

suspend fun main(args: Array<String>) {
    val server = createServerWithRegisteredAgents()
    val questionSet: GaiaQuestionSet = GaiaConfig.gaiaQuestionSet
    val questions = loadGaiaQuestions(questionSet.metadataFile)

    GaiaApplication(server).apply {
        server.start()
        startAnswerServer(wait = false)

        var correctAnswersCount = 0
        var allAnswersCount = 0
        val questionAnswerPairs = mutableListOf<Pair<GaiaQuestion, GaiaAnswerAttempt>>()
        val results = mutableSetOf<GaiaResult>()
        val existingResults = loadAllGaiaResults()
        val questionsWithoutCorrectAnswers = questions.filter { question ->
            existingResults.none { result ->
                result.question.taskId == question.taskId && result.isCorrect
            }
        }
        val questionsWithCorrectAnswers = questions.filter { question ->
            existingResults.any { result ->
                result.question.taskId == question.taskId && result.isCorrect
            }
        }
        val questionsWithAnyAnswers = questions.filter { question ->
            existingResults.any { result ->
                result.question.taskId == question.taskId
            }
        }
        val percentOfAnsweredQuestionsCorrect = if (questionsWithAnyAnswers.isNotEmpty()) {
            questionsWithCorrectAnswers.size.toDouble() / questionsWithAnyAnswers.size * 100
        } else {
            0.0
        }
        println("Percent of questions with correct answers: $percentOfAnsweredQuestionsCorrect%")
        println("Total questions: ${questions.size}, Questions with correct answers: ${questionsWithCorrectAnswers.size}, Questions without correct answers: ${questionsWithoutCorrectAnswers.size}, Questions with any answers: ${questionsWithAnyAnswers.size}")
        println("Total correct answers: ${existingResults.count { it.isCorrect }}, Total incorrect answers: ${existingResults.count { !it.isCorrect }}")
        val semaphore = Semaphore(2)
        val context = CoroutineScope(SupervisorJob())
        questions.map { question ->
            context.async {
                semaphore.withPermit {
                    try {
                        withTimeout(600 * 1000) {
                            val startTime = System.currentTimeMillis()
                            val answerDeferred = findAnswer(question)
                            println("Waiting for answer to question: ${question.question}")
                            val answerAttempt = answerDeferred.await()
                            println("${answerAttempt.questionId} took ${(System.currentTimeMillis() - startTime) / 1000}s")
                            if (question.finalAnswer.lowercase() != answerAttempt.answer.lowercase()) {
                                println("The answer attempt is incorrect! Expected: ${question.finalAnswer}, got: ${answerAttempt.answer}")
                            } else {
                                println("The answer attempt is correct!")
                                correctAnswersCount++
                            }
                            val relevantSession = server.sessionManager.getSession(answerAttempt.sessionId)
                                ?: throw IllegalStateException("Session not found for answer attempt: ${answerAttempt.sessionId}")
                            val questionAnswerPair = question to answerAttempt
                            val result = GaiaResult(
                                question = question,
                                answerAttempt = answerAttempt,
                                isCorrect = question.finalAnswer.lowercase() == answerAttempt.answer.lowercase(),
                                relevantSession.getAllThreads().map { it.resolve() }
                            )
                            saveResultToFile(result)
                            results.add(result)
                            questionAnswerPairs.add(questionAnswerPair)
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
            }
        }.awaitAll()

        File("coral-GAIA/answers.json").writeText(Json.encodeToString(results))
        val json = Json.encodeToString(questionAnswerPairs)
        File("coral-GAIA/results+${System.currentTimeMillis()}.json").writeText(json)
        server.stop()
    }
}
