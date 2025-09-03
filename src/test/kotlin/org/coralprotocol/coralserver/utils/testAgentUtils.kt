package org.coralprotocol.coralserver.utils

import ai.koog.agents.core.agent.AIAgentLoopContext
import ai.koog.agents.core.agent.ActAIAgent
import ai.koog.agents.core.agent.actAIAgent
import ai.koog.agents.core.agent.containsToolCalls
import ai.koog.agents.core.agent.executeMultipleTools
import ai.koog.agents.core.agent.extractToolCalls
import ai.koog.agents.core.agent.requestLLMMultiple
import ai.koog.agents.core.agent.sendMultipleToolResults
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
import kotlinx.datetime.Clock
import org.coralprotocol.coralserver.mcp.McpResources

import org.coralprotocol.coralserver.utils.ExternalSteppingKoogBuilder.Companion.build
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

suspend fun AIAgentLoopContext.updateSystemResources(client: Client) {
    val newSystemMessage = Message.System(
        injectedWithMcpResources(client, getOriginalSystemPrompt()),
        RequestMetaInfo(Clock.System.now())
    )
    return llm.writeSession {
        rewritePrompt { prompt ->
            val withoutSystem = prompt.messages.drop(1)
            prompt.copy(messages = listOf(newSystemMessage) + withoutSystem)
        }
    }
}

private val defaultSystemPrompt = """
You have access to communication tools to interact with other agents.

You can emit as many messages as you like before finishing with other agents.

Don't try to guess facts; ask other agents or use resources.

If given a simple task, wait briefly for mentions and then return the result.
""".trimIndent()

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

private fun getOriginalSystemPrompt(): String = """
You are an agent connected to Coral.

-- Start of resources --
<resource>${McpResources.MESSAGE_RESOURCE_URI}</resource>
-- End of resources --
""".trimIndent()


interface ServerConnectionCoreDetails {
    val protocol: String
    val host: String
    val port: UShort
    val namePassedToServer: String
    val descriptionPassedToServer: String
}
data class ServerConnectionCoreDetailsImpl(
    override val protocol: String,
    override val host: String,
    override val port: UShort,
    override val namePassedToServer: String,
    override val descriptionPassedToServer: String = namePassedToServer,
) : ServerConnectionCoreDetails

interface ServerConnectionLocalDetails : ServerConnectionCoreDetails {
    val applicationId: String
    val privacyKey: String
    val sessionId: String
}

data class ServerConnectionLocalDetailsImpl(
    override val protocol: String,
    override val host: String,
    override val port: UShort,
    override val applicationId: String,
    override val privacyKey: String,
    override val sessionId: String,
    override val namePassedToServer: String,
    override val descriptionPassedToServer: String = namePassedToServer,
) : ServerConnectionLocalDetails

@OptIn(ExperimentalUuidApi::class)
suspend fun createConnectedKoogAgent(
    server: ServerConnectionCoreDetails,
    renderServerUrl: ServerConnectionCoreDetails.() -> String,
    systemPrompt: String = defaultSystemPrompt,
    modelName: LLModel = OpenAIModels.Chat.GPT4o,
): ExternalSteppingKoog {
    val renderedServerUrl = renderServerUrl(server)
    val executor: PromptExecutor = simpleOpenAIExecutor(
        System.getenv("OPENAI_API_KEY")
            ?: throw IllegalArgumentException("OPENAI_API_KEY not set")
    )

    val mcpClient = getMcpClient(renderedServerUrl)
    val toolRegistry = McpToolRegistryProvider.fromClient(mcpClient)
    return ExternalSteppingKoogBuilder(loopStep = { newInputMessage ->
        updateSystemResources(mcpClient)
        var responses = requestLLMMultiple(newInputMessage.content)

        while (responses.containsToolCalls()) {
            updateSystemResources(mcpClient)
            val tools = extractToolCalls(responses)
            val results = executeMultipleTools(tools)
            responses = sendMultipleToolResults(results)
        }
        println("Response: $responses")

    })
        .withKoogAgent { loop: suspend AIAgentLoopContext.(Nothing?) -> Unit ->
            actAIAgent(
                prompt = systemPrompt,
                promptExecutor = executor,
                model = modelName,
                toolRegistry = toolRegistry,
                loop = loop
            ) as ActAIAgent<Nothing?, Unit>
        }.build()
}