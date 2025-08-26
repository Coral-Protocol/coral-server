package org.coralprotocol.coralserver.e2e

import ai.koog.agents.core.agent.AIAgentLoopContext
import ai.koog.agents.core.agent.ActAIAgent
import ai.koog.agents.core.agent.actAIAgent
import ai.koog.agents.core.agent.requestLLM
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.PatchedSseClientTransport
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.CoralAgentGraphSession
import org.coralprotocol.coralserver.session.SessionManager
import kotlin.uuid.ExperimentalUuidApi

/**
 * Creates a Coral session with connected Koog agents.
 */
@OptIn(ExperimentalUuidApi::class)
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
        privacyKey = privacyKey,
        agentGraph = null,
    )

    val context = BasicSessionCoralAgentDefinitionContext(server)
    agentsInSessionBlock(context)
    if (noAgentsOptional) {
        session.devRequiredAgentStartCount = context.buildKoogAgents(session).size
    }
    context.buildKoogAgents(session)
    context.onAgentsCreated(SessionCoralAgentDefinitionContext.AgentsCreatedContext())
    return session
}

interface SessionCoralAgentDefinitionContext {
    val server: CoralServer

    @OptIn(ExperimentalUuidApi::class)
    fun agent(
        name: String,
        description: String = name,
        systemPrompt: String = defaultSystemPrompt,
        modelName: LLModel = OpenAIModels.Chat.GPT4o
    ): Deferred<ActAIAgent<Nothing?, Nothing?>>

    class AgentsCreatedContext {
        @OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
        suspend fun Deferred<ActAIAgent<Nothing?, Nothing?>>.getConnected() = this.getCompleted()
    }

    var onAgentsCreated: suspend AgentsCreatedContext.() -> Unit
}

private class BasicSessionCoralAgentDefinitionContext(
    override val server: CoralServer
) : SessionCoralAgentDefinitionContext {

    override var onAgentsCreated: suspend SessionCoralAgentDefinitionContext.AgentsCreatedContext.() -> Unit = { }

    @OptIn(ExperimentalUuidApi::class)
    private val agentsToAdd = mutableListOf<suspend (CoralAgentGraphSession) -> ActAIAgent<Nothing?, Nothing?>>()

    @OptIn(ExperimentalUuidApi::class)
    override fun agent(
        name: String,
        description: String,
        systemPrompt: String,
        modelName: LLModel
    ): Deferred<ActAIAgent<Nothing?, Nothing?>> {
        val deferrable = CompletableDeferred<ActAIAgent<Nothing?, Nothing?>>()
        agentsToAdd.add { session ->
            val connected = createConnectedKoogAgent(
                server = server,
                namePassedToServer = name,
                descriptionPassedToServer = description,
                systemPrompt = systemPrompt,
                modelName = modelName,
                sessionId = session.id,
                privacyKey = session.privacyKey,
                applicationId = session.applicationId
            )
            deferrable.complete(connected)
            return@add connected
        }
        return deferrable
    }

    val context = newFixedThreadPoolContext(5, "E2EResourceTest")

    @OptIn(ExperimentalUuidApi::class)
    suspend fun buildKoogAgents(session: CoralAgentGraphSession): List<ActAIAgent<Nothing?, Nothing?>> =
        coroutineScope {
            return@coroutineScope agentsToAdd.map {
                async(context) { it(session) }
            }.awaitAll()
        }
}

/**
 * Creates a connected Koog agent.
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun createConnectedKoogAgent(
    server: CoralServer,
    namePassedToServer: String,
    descriptionPassedToServer: String = namePassedToServer,
    systemPrompt: String = "You are a helpful assistant.",
    modelName: LLModel = OpenAIModels.Chat.GPT4o,
    sessionId: String = "session1",
    privacyKey: String = "privkey",
    applicationId: String = "exampleApplication",
): ActAIAgent<Nothing?, Nothing?> = createConnectedKoogAgent(
    protocol = "http",
    host = server.host,
    port = server.port,
    namePassedToServer = namePassedToServer,
    descriptionPassedToServer = descriptionPassedToServer,
    systemPrompt = systemPrompt,
    modelName = modelName,
    sessionId = sessionId,
    privacyKey = privacyKey,
    applicationId = applicationId,
)

@OptIn(ExperimentalUuidApi::class)
suspend fun createConnectedKoogAgent(
    protocol: String = "http",
    host: String = "localhost",
    port: UShort,
    namePassedToServer: String,
    descriptionPassedToServer: String = namePassedToServer,
    systemPrompt: String = "You are a helpful assistant.",
    modelName: LLModel = OpenAIModels.Chat.GPT4o,
    sessionId: String = "session1",
    privacyKey: String = "privkey",
    applicationId: String = "exampleApplication",
): ActAIAgent<Nothing?, Nothing?> {
    val coralUrl =
        "$protocol://$host:$port/sse/v1/devmode/$applicationId/$privacyKey/$sessionId/sse?agentId=$namePassedToServer"

    val executor: PromptExecutor = simpleOpenAIExecutor(
        System.getenv("OPENAI_API_KEY")
            ?: throw IllegalArgumentException("OPENAI_API_KEY not set")
    )

    val mcpClient = runBlocking { getMcpClient(coralUrl) }
    val toolRegistry = McpToolRegistryProvider.fromClient(mcpClient)

    val agent = actAIAgent<Nothing?, Nothing?>(
        prompt = systemPrompt,
        promptExecutor = executor,
        model = modelName,
        toolRegistry = toolRegistry,
    ) {
        println("Bootstrapping agent $namePassedToServer ...")
        updateSystemResources(mcpClient, coralUrl)
        val response = requestLLM("hi") // hack to ensure connection
        println("Initial response: $response")
        return@actAIAgent null
    }

    return agent as ActAIAgent<Nothing?, Nothing?>
}

private suspend fun getMcpClient(serverUrl: String): Client {
    val transport = PatchedSseClientTransport(
        client = HttpClient { install(SSE) },
        urlString = serverUrl,
    )
    val client = Client(clientInfo = Implementation("koog-mcp-client", "1.0"))
    client.connect(transport)
    return client
}

suspend fun AIAgentLoopContext.updateSystemResources(client: Client, coralConnectionUrl: String) {
    val newSystemMessage = Message.System(
        injectedWithMcpResources(client, getOriginalSystemPrompt(coralConnectionUrl)),
        RequestMetaInfo(kotlinx.datetime.Clock.System.now())
    )
    return llm.writeSession {
        rewritePrompt { prompt ->
            val withoutSystem = prompt.messages.drop(1)
            prompt.copy(messages = listOf(newSystemMessage) + withoutSystem)
        }
    }
}

private suspend fun injectedWithMcpResources(client: Client, original: String): String {
    val resourceRegex = "<resource>(.*?)</resource>".toRegex()
    val matches = resourceRegex.findAll(original)
    val uris = matches.map { it.groupValues[1] }.toList()
    if (uris.isEmpty()) return original

    val resolvedResources = uris.map { uri ->
        val resource = client.readResource(io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest(uri = uri))
        val contents = resource?.contents?.joinToString("\n") {
            (it as io.modelcontextprotocol.kotlin.sdk.TextResourceContents).text
        } ?: throw IllegalStateException("No contents for $uri")
        "<resource uri=\"$uri\">\n$contents\n</resource>"
    }

    var result = original
    matches.forEachIndexed { i, match -> result = result.replace(match.value, resolvedResources[i]) }
    return result
}

private fun getOriginalSystemPrompt(coralConnectionUrl: String): String = """
You are an agent connected to Coral.

-- Start of resources --
<resource>coral://${(coralConnectionUrl).substringAfter("http://")}</resource>
-- End of resources --
""".trimIndent()

class TestCoralServer(
    val host: String = "0.0.0.0",
    val port: UShort = 5555u,
    val devmode: Boolean = false,
    val sessionManager: SessionManager = SessionManager(port = port),
) {
    var server: CoralServer? = null

    @OptIn(DelicateCoroutinesApi::class)
    val serverContext = newFixedThreadPoolContext(1, "InlineTestCoralServer")

    @OptIn(DelicateCoroutinesApi::class)
    fun setup() {
        server?.stop()
        server = CoralServer(
            host = host,
            port = port,
            devmode = devmode,
            sessionManager = sessionManager,
            appConfig = ConfigCollection(null)
        )
        GlobalScope.launch(serverContext) {
            server!!.start()
        }
    }
}

private val defaultSystemPrompt = """
You have access to communication tools to interact with other agents.

You can emit as many messages as you like before finishing with other agents.

Don't try to guess facts; ask other agents or use resources.

If given a simple task, wait briefly for mentions and then return the result.
""".trimIndent()
