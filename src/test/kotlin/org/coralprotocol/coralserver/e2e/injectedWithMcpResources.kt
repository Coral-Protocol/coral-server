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
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.coralprotocol.coralserver.ExternalSteppingKoogHandle
import org.coralprotocol.coralserver.server.CoralServer
import kotlin.uuid.ExperimentalUuidApi

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
        RequestMetaInfo(Clock.System.now())
    )
    return llm.writeSession {
        rewritePrompt { prompt ->
            val withoutSystem = prompt.messages.drop(1)
            prompt.copy(messages = listOf(newSystemMessage) + withoutSystem)
        }
    }
}

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
): ActAIAgent<Nothing?, Unit> = createConnectedKoogAgent(
    protocol = "http",
    host = server.host,
    descriptionPassedToServer = descriptionPassedToServer,
    port = server.port,
    namePassedToServer = namePassedToServer,
    systemPrompt = systemPrompt,
    modelName = modelName,
    sessionId = sessionId,
    privacyKey = privacyKey,
    applicationId = applicationId,
)

private suspend fun injectedWithMcpResources(client: Client, original: String): String {
    val resourceRegex = "<resource>(.*?)</resource>".toRegex()
    val matches = resourceRegex.findAll(original)
    val uris = matches.map { it.groupValues[1] }.toList()
    if (uris.isEmpty()) return original

    val resolvedResources = uris.map { uri ->
        val resource = client.readResource(ReadResourceRequest(uri = uri))
        val contents = resource?.contents?.joinToString("\n") {
            (it as TextResourceContents).text
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


@JvmInline
@OptIn(ExperimentalUuidApi::class)
value class ExternallySteppedKoogAgent private constructor(val koogAgent: ActAIAgent<Nothing?, Unit>)

@OptIn(ExperimentalUuidApi::class)
suspend fun createConnectedKoogAgent(
    protocol: String = "http",
    host: String = "localhost",
    port: UShort,
    namePassedToServer: String,
    descriptionPassedToServer: String = namePassedToServer,
    systemPrompt: String = "You are a helpful assistant.",
    devmode: Boolean = true,
    modelName: LLModel = OpenAIModels.Chat.GPT4o,
    sessionId: String = "session1",
    privacyKey: String = "privkey",
    applicationId: String = "exampleApplication",
): ActAIAgent<Nothing?, Unit> {
    val devmodePath = if (devmode) "devmode/" else ""
    val coralUrl =
        "$protocol://$host:$port/sse/v1/$devmodePath$applicationId/$privacyKey/$sessionId/sse?agentId=$namePassedToServer&agentDescription=$descriptionPassedToServer"

    val executor: PromptExecutor = simpleOpenAIExecutor(
        System.getenv("OPENAI_API_KEY")
            ?: throw IllegalArgumentException("OPENAI_API_KEY not set")
    )

    val mcpClient = runBlocking { getMcpClient(coralUrl) }
    val toolRegistry = McpToolRegistryProvider.fromClient(mcpClient)
    val externalSteppingKoogHandle = ExternalSteppingKoogHandle(agent = null, beforeStep = {
        updateSystemResources(mcpClient, coralUrl)
    })
    val agent = actAIAgent(
        prompt = systemPrompt,
        promptExecutor = executor,
        model = modelName,
        toolRegistry = toolRegistry,
        loop = externalSteppingKoogHandle.getLoop() as suspend AIAgentLoopContext.(Nothing?) -> Unit,
    )

    externalSteppingKoogHandle.agent = agent
    externalSteppingKoogHandle.build() // gives you real handle

    return agent as ActAIAgent<Nothing?, Unit>
}
