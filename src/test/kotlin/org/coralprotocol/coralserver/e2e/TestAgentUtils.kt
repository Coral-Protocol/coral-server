package org.coralprotocol.coralserver.e2e

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.MissingToolsConversionStrategy
import ai.koog.agents.core.agent.config.ToolCallDescriber
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.config.AppConfig
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.CoralAgentGraphSession
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * Creates a session with connected agents using koog's AIAgent framework.
 */
suspend fun createSessionWithConnectedAgents(
    server: CoralServer,
    sessionId: String,
    privacyKey: String,
    applicationId: String,
    noAgentsOptional: Boolean = true,
    agentsInSessionBlock: suspend MultiAgentDefinitionContext.() -> Unit,
): CoralAgentGraphSession {
    val session = server.sessionManager.getOrCreateSession(
        sessionId = sessionId,
        applicationId = applicationId,
        privacyKey = privacyKey
    )
    val context = MultiAgentDefinitionContext(server, session)
    agentsInSessionBlock(context)

    if (noAgentsOptional) {
        session.devRequiredAgentStartCount = context.agents.size
    }
    context.initializeAgents()
    return session
}


class TestAgentDefinition(val deferredAgent: Deferred<TestCoralAgent>)

@OptIn(ExperimentalCoroutinesApi::class)
interface AgentsCreatedContext {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun TestAgentDefinition.get(): TestCoralAgent {
        return deferredAgent.getCompleted()
    }

    suspend fun TestAgentDefinition.ask(message: String): String {
        return deferredAgent.getCompleted().ask(message)
    }
}

/**
 * Context for defining agents in a session.
 */
class MultiAgentDefinitionContext(
    val server: CoralServer,
    val session: CoralAgentGraphSession,

    ) {
    var onAgentsCreated: suspend AgentsCreatedContext.() -> Unit = {}

    val agents = mutableListOf<Deferred<TestCoralAgent>>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Define an agent that will be connected to the Coral server.
     */
    fun agent(
        name: String,
        description: String = name,
        systemPrompt: String = defaultSystemPrompt,
        modelName: String = "gpt-4o",
        openAiApiKey: String = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY not set"),
    ): TestAgentDefinition {
        val config = TestCoralAgentConfig(
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            modelName = modelName,
            openAiApiKey = openAiApiKey
        )
        val deferred = coroutineScope.async { createConnectedAgent(config) }
        agents.add(deferred)

        return TestAgentDefinition(deferred)
    }


    /**
     * Initialize all agents and wait for them to connect.
     */
    suspend fun initializeAgents() {
        agents.awaitAll()
        onAgentsCreated(object : AgentsCreatedContext {})
    }

    private fun defaultSseTransport(urlString: String): PatchedSseClientTransport {
        // Setup SSE transport using the HTTP client
        return PatchedSseClientTransport(
            client = HttpClient {
                install(SSE)
            },
            urlString = urlString,
        )
    }

    private suspend fun createConnectedAgent(config: TestCoralAgentConfig): TestCoralAgent {
        // Create MCP SSE URL for this agent
        val mcpUrl = buildMcpUrl(
            server = server,
            sessionId = session.id,
            privacyKey = session.privacyKey,
            applicationId = session.applicationId,
            agentId = config.name
        )

        logger.info { "Connecting agent ${config.name} to MCP at: $mcpUrl" }

        // Create tool registry from MCP connection
        val toolRegistry = try {
            McpToolRegistryProvider.fromTransport(
                transport = defaultSseTransport(mcpUrl),
                name = config.name,
                version = "1.0.0"
            )
        } catch (e: Exception) {
            logger.error { "Failed to connect agent ${config.name} to MCP: ${e.message}" }
            throw e
        }

        val executor = simpleOpenAIExecutor(config.openAiApiKey)
        val conversation = mutableListOf<Message>()
        val loopingChatStrategy = strategy("my-strategy") {
            val nodeCallLLM by nodeChatLLMRequest(conversation = conversation)
            val executeToolCall by loggedToolCall(config, conversation = conversation)
            val sendToolResult by nodeLLMSendToolResult(conversation = conversation)

            edge(nodeStart forwardTo nodeCallLLM)
            edge(nodeCallLLM forwardTo executeToolCall onToolCall { true })
            edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })

            edge(executeToolCall forwardTo sendToolResult)
            edge(sendToolResult forwardTo executeToolCall onToolCall { true })
            edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
        }

        val agent = AIAgent(
            executor,
            strategy = loopingChatStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt(Prompt.Empty) {
                    system(config.systemPrompt)
                },
                model = OpenAIModels.Chat.GPT4o,
                maxAgentIterations = 20,
                missingToolsConversionStrategy = MissingToolsConversionStrategy.All(format = ToolCallDescriber.JSON)
            ),
            toolRegistry = toolRegistry,
        )

        return TestCoralAgent(config, agent, conversation)
    }
}

/**
 * Build the MCP SSE URL for an agent.
 */
private fun buildMcpUrl(
    server: CoralServer,
    sessionId: String,
    privacyKey: String,
    applicationId: String,
    agentId: String,
    protocol: String = "http",
    maxWaitForMentionsTimeout: Long = 3000L,
): String {
    return "$protocol://${server.host}:${server.port}/devmode/$applicationId/$privacyKey/$sessionId/sse?agentId=$agentId&maxWaitForMentionsTimeout=$maxWaitForMentionsTimeout"
}

/**
 * Test server configuration for Coral.
 */
class TestCoralServer(
    val host: String = "0.0.0.0",
    val port: UShort = 5555u,
    val devmode: Boolean = true,
    val sessionManager: SessionManager =
        SessionManager(port = port),
) {
    var server: CoralServer? = null

    @OptIn(DelicateCoroutinesApi::class)
    val serverContext = newFixedThreadPoolContext(1, "TestCoralServer")

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
    }

    fun stop() {
        server?.stop()
    }
}