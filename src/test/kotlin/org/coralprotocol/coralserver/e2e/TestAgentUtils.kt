package org.coralprotocol.coralserver.e2e

import com.azure.ai.openai.OpenAIClientBuilder
import com.azure.core.credential.KeyCredential
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.every
import io.mockk.mockkStatic
import io.modelcontextprotocol.util.Utils
import io.modelcontextprotocol.util.Utils.resolveUri
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.config.AppConfig
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.CoralAgentGraphSession
import org.coralprotocol.coralserver.session.SessionManager
import org.eclipse.lmos.arc.agents.AgentFailedException
import org.eclipse.lmos.arc.agents.ChatAgent
import org.eclipse.lmos.arc.agents.ConversationAgent
import org.eclipse.lmos.arc.agents.User
import org.eclipse.lmos.arc.agents.agent.ask
import org.eclipse.lmos.arc.agents.agents
import org.eclipse.lmos.arc.agents.conversation.Conversation
import org.eclipse.lmos.arc.agents.conversation.ConversationMessage
import org.eclipse.lmos.arc.agents.dsl.AllTools
import org.eclipse.lmos.arc.agents.llm.AIClientConfig
import org.eclipse.lmos.arc.agents.llm.MapChatCompleterProvider
import org.eclipse.lmos.arc.client.azure.AzureAIClient
import org.eclipse.lmos.arc.core.Failure
import org.eclipse.lmos.arc.core.Result
import org.eclipse.lmos.arc.core.Success
import org.eclipse.lmos.arc.mcp.McpTools
import java.net.URI
import java.net.http.HttpRequest
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val logger = KotlinLogging.logger {}

suspend fun createSessionWithConnectedAgents(
    server: CoralServer,
    sessionId: String,
    privacyKey: String,
    applicationId: String,
    noAgentsOptional: Boolean = true,
    agentsInSessionBlock: SessionCoralAgentDefinitionContext.() -> Unit,
): CoralAgentGraphSession {
    val session = server.sessionManager.getOrCreateSession(
        sessionId = sessionId,
        applicationId = applicationId,
        privacyKey = privacyKey
    )

    val context = BasicSessionCoralAgentDefinitionContext(server)
    agentsInSessionBlock(context)
    if (noAgentsOptional) {
        session.devRequiredAgentStartCount = context.buildChatAgents(session).size
    }
    context.buildChatAgents(session)
    context.onAgentsCreated(SessionCoralAgentDefinitionContext.AgentsCreatedContext())
    return session
}

interface SessionCoralAgentDefinitionContext {
    val server: CoralServer
    fun agent(
        name: String,
        description: String = name,
        systemPrompt: String = defaultSystemPrompt,
        modelName: String = "gpt-4o",
        clientBuilder: OpenAIClientBuilder = getDefaultOpenAIClientBuilder(),
        clientConfiguration: AIClientConfig = AIClientConfig(
            modelName = modelName,
            apiKey = System.getenv("OPENAI_API_KEY"),
            endpoint = "https://api.openai.com/v1",
            client = "?"
        ),
    ): Deferred<ActuallyAgenticAgent>

    class AgentsCreatedContext {
        // TODO: Consider using a more specific type than Deferred<ChatAgent>
        // TODO: Consider using more guaranteed invokeCompleted
        @OptIn(ExperimentalCoroutinesApi::class)
        fun Deferred<ActuallyAgenticAgent>.getConnected() = this.getCompleted()

        suspend fun Deferred<ActuallyAgenticAgent>.askC(question: String, user: User? = null): String {
            val agent = this.getConnected()
            val response = agent.ask(question, user)
            return when (response) {
                is Success -> {
                    logger.info { "${agent.name} -> [User]: ${response.value}" }
                    response.value
                }

                is Failure -> throw AgentFailedException("Agent failed to respond: ${response.reason}")
            }
        }
    }

    var onAgentsCreated: suspend AgentsCreatedContext.() -> Unit
}

class ActuallyAgenticAgent (
    val wrappedLmosChatAgent: ChatAgent,
    val rememberedTranscript: MutableList<ConversationMessage> = mutableListOf()
) : ConversationAgent by wrappedLmosChatAgent {
    // TODO: Have the events sent here so the memory can be updated

    override suspend fun execute(
        input: Conversation,
        context: Set<Any>
    ): Result<Conversation, AgentFailedException> {
        val combinedConversation = input.copy(transcript = rememberedTranscript + input.transcript)
        // Ensure there are no duplicated messages in case a future version of lmos remembers conversations internally
        val messagesAreDuplicated = combinedConversation.transcript
            .filterNot { it.turnId == null }
            .groupBy { it.turnId }
            .any { it.value.size != 1 }
        if (messagesAreDuplicated) {
            throw IllegalStateException("Conversation contains duplicated messages. Probably underlying behaviour has changed away from expected")
        }

        val result: Success<Conversation> =
            wrappedLmosChatAgent.execute(combinedConversation, context) as Success<Conversation>

        rememberedTranscript.addAll(result.value.transcript)
        return result
    }
}

private class BasicSessionCoralAgentDefinitionContext(override val server: CoralServer) :
    SessionCoralAgentDefinitionContext {
    override var onAgentsCreated: suspend SessionCoralAgentDefinitionContext.AgentsCreatedContext.() -> Unit = { }
    private val agentsToAdd = mutableListOf<suspend (CoralAgentGraphSession) -> ActuallyAgenticAgent>()
    override fun agent(
        name: String,
        description: String,
        systemPrompt: String,
        modelName: String,
        clientBuilder: OpenAIClientBuilder,
        clientConfiguration: AIClientConfig
    ): Deferred<ActuallyAgenticAgent> {
        val deferrable = CompletableDeferred<ActuallyAgenticAgent>()
        agentsToAdd.add { session ->
            val createConnectedCoralAgent = createConnectedCoralAgent(
                server = server,
                namePassedToServer = name,
                descriptionPassedToServer = description,
                systemPrompt = systemPrompt,
                agentClientConfiguration = clientConfiguration,
                agentClientBuilder = clientBuilder,
                sessionId = session.id,
                privacyKey = session.privacyKey,
                applicationId = session.applicationId
            )
            deferrable.complete(createConnectedCoralAgent)
            return@add createConnectedCoralAgent
        }
        return deferrable
    }

    val context = newFixedThreadPoolContext(5, "E2EResourceTest")
    suspend fun buildChatAgents(session: CoralAgentGraphSession): List<ActuallyAgenticAgent> = coroutineScope {
        return@coroutineScope agentsToAdd.map {
            async(context) { it(session) }
        }.awaitAll()
    }
}


fun createConnectedCoralAgent(
    server: CoralServer,
    namePassedToServer: String,
    descriptionPassedToServer: String = namePassedToServer,
    systemPrompt: String = "You are a helpful assistant.",
    agentClientConfiguration: AIClientConfig = AIClientConfig(
        modelName = "gpt-4o",
        apiKey = System.getenv("OPENAI_API_KEY"),
        endpoint = "https://api.openai.com/v1",
        client = "?"
    ),
    agentClientBuilder: OpenAIClientBuilder = getDefaultOpenAIClientBuilder(),
    sessionId: String = "session1",
    privacyKey: String = "privkey",
    applicationId: String = "exampleApplication",
): ActuallyAgenticAgent = createConnectedCoralAgent(
    "http",
    server.host,
    server.port,
    namePassedToServer,
    descriptionPassedToServer,
    systemPrompt,
    agentClientConfiguration,
    agentClientBuilder,
    sessionId,
    privacyKey,
    applicationId
)

/**
 * Creates a connected Coral agent.
 *
 * @param port The port to connect to.
 * @param namePassedToServer The name of the agent passed to the server.
 * @param descriptionPassedToServer The description of the agent passed to the server.
 * @param systemPrompt The system prompt for the agent.
 * @param agentClient The AzureAIClient to use.
 * @param sessionId The session ID for the agent.
 * @param privacyKey The privacy key for the agent.
 * @param applicationId The application ID for the agent.
 * @return The created agent.
 */

fun createConnectedCoralAgent(
    protocol: String = "http",
    host: String = "localhost",
    port: UShort,
    namePassedToServer: String,
    descriptionPassedToServer: String = namePassedToServer,
    systemPrompt: String = "You are a helpful assistant.",
    agentClientConfiguration: AIClientConfig = AIClientConfig(
        modelName = "gpt-4o",
        apiKey = System.getenv("OPENAI_API_KEY"),
        endpoint = "https://api.openai.com/v1",
        client = "?"
    ),
    agentClientBuilder: OpenAIClientBuilder = getDefaultOpenAIClientBuilder(),
    sessionId: String = "session1",
    privacyKey: String = "privkey",
    applicationId: String = "exampleApplication",
    maxWaitForMentionsTimeout: Long = 3000L,
): ActuallyAgenticAgent {
    val openAiClient = agentClientBuilder.buildAsyncClient()
    val azureAIClient = AzureAIClient(agentClientConfiguration, openAiClient)

    val forgetfulLmosAgent = agents(
        functionLoaders = listOf(
            McpTools(
                "$protocol://$host:$port/devmode/$applicationId/$privacyKey/$sessionId/sse?agentId=$namePassedToServer&maxWaitForMentionsTimeout=$maxWaitForMentionsTimeout",
                5000.seconds.toJavaDuration()
            )
        ),
        chatCompleterProvider = MapChatCompleterProvider(mapOf(agentClientConfiguration.modelName!! to azureAIClient)),
    ) {
        agent {
            this@agent.name = namePassedToServer
            this@agent.description = descriptionPassedToServer

            model {
                agentClientConfiguration.modelName!!
            }

            prompt { systemPrompt }
            tools = AllTools
        }
    }.getAgents().first() as ChatAgent
    runBlocking {
        forgetfulLmosAgent.ask("hi") // TODO: This is a hack to make sure the agent is connected.
        //TODO: Make arc connect to MCP servers eagerly.
    }
    val actuallyAgenticAgent = ActuallyAgenticAgent(
        forgetfulLmosAgent
        return actuallyAgenticAgent
}

///**
// * Creates an AzureAIClient for testing.
// *
// * @return An AzureAIClient configured for testing.
// */
//fun createTestAIClient(): AzureAIClient {
//    val config = AIClientConfig(
//        modelName = "gpt-4o",
//        apiKey = System.getenv("OPENAI_API_KEY"),
//        endpoint = "https://api.openai.com/v1",
//        client = "?"
//    )
//    val azureOpenAIClient: OpenAIClientBuilder = OpenAIClientBuilder()
//        .endpoint(config.endpoint)
//        .credential(KeyCredential(config.apiKey))
//
//    return AzureAIClient(config, azureOpenAIClient, eventHandler = {
//        logger.info { "AzureAIClient event: $it" }
//    })
//}

fun getDefaultOpenAIClientBuilder(): OpenAIClientBuilder {
    return OpenAIClientBuilder()

        .endpoint("https://api.openai.com/v1")
        .credential(KeyCredential(System.getenv("OPENAI_API_KEY")))
}

class TestCoralServer(
    val host: String = "0.0.0.0",
    val port: UShort = 5555u,
    val devmode: Boolean = false,
    val sessionManager: SessionManager = SessionManager(port = port),
) {
    var server: CoralServer? = null

    @OptIn(DelicateCoroutinesApi::class)
    val serverContext = newFixedThreadPoolContext(1, "E2EResourceTest")

    @OptIn(DelicateCoroutinesApi::class)
    fun setup() {
        server?.stop()
        server = CoralServer(
            host = host,
            port = port,
            devmode = devmode,
            sessionManager = sessionManager,
            appConfig = AppConfig(emptyList())
        )
        GlobalScope.launch(serverContext) {
            server!!.start()
        }
        patchMcpJavaContentType()
        patchMcpJavaEndpointResolution()
    }
}


private fun patchMcpJavaContentType() {
    mockkStatic(HttpRequest::class)
    every { HttpRequest.newBuilder() } answers {
        println("MockK Interceptor [@BeforeEach]: HttpRequest.newBuilder() called. ")
        val requestBuilder =
            callOriginal().headers("Content-Type", "application/json").timeout(40.seconds.toJavaDuration())
        return@answers requestBuilder
    }
}

private fun patchMcpJavaEndpointResolution() {
    mockkStatic(Utils::class)
    every { resolveUri(any<URI>(), any<String>()) } answers {
        val baseUrl = invocation.args[0] as URI
        val endpointUrl = invocation.args[1] as String
        println("MockK Interceptor [@BeforeEach]: Utils.resolveUri called with baseUrl='$baseUrl', endpointUrl='$endpointUrl'. ")
        return@answers if (endpointUrl.contains("?sessionId")) {
            // In this case the sessionId is an MCP sessionId, not a Coral sessionId.
            // The resolution logic works in this case (though the original is resolving against a URI object)
            baseUrl.resolve(endpointUrl)
        } else {
            baseUrl
        }
    }
}

private val defaultSystemPrompt = """
You have access to communication tools to interact with other agents.

If there are no other agents, remember to re-list the agents periodically using the list tool.

You should know that the user can't see any messages you send, you are expected to be autonomous and respond to the user only when you have finished working with other agents, using tools specifically for that.

You can emit as many messages as you like before using that tool when you are finished or absolutely need user input. You are on a loop and will see a "user" message every 4 seconds, but it's not really from the user.

Run the wait for mention tool when you are ready to receive a message from another agent. This is the preferred way to wait for messages from other agents.

You'll only see messages from other agents since you last called the wait for mention tool. Remember to call this periodically. Also call this when you're waiting with nothing to do.

Don't try to guess any numbers or facts, only use reliable sources. If you are unsure, ask other agents for help.

If you have been given a simple task by the user, you can use the wait for mention tool once with a short timeout and then return the result to the user in a timely fashion.
""".trimIndent()