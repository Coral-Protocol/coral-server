@file:OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)

package org.coralprotocol.coralserver.agent.runtime

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import dev.eav.tomlkt.TomlClassDiscriminator
import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import org.coralprotocol.coralserver.agent.exceptions.PrototypeRuntimeException
import org.coralprotocol.coralserver.agent.runtime.prototype.*
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat
import org.coralprotocol.coralserver.logging.LoggingInterface
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@Serializable
@JsonClassDiscriminator("prototype")
@TomlClassDiscriminator("prototype")
data class PrototypeRuntime(
    val volatile: Boolean = false,

    @SerialName("proxy")
    val proxyName: PrototypeString,

    val client: PrototypeClient? = null,

    @SerialName("iterations")
    val iterationCount: PrototypeInteger = PrototypeInteger.Inline(20),

    @SerialName("delay")
    val iterationDelay: PrototypeInteger = PrototypeInteger.Inline(0),

    val prompts: PrototypePrompts = PrototypePrompts(),

    @SerialName("tools")
    val toolServers: List<PrototypeToolServer> = listOf(),

    @Transient
    /**
     * Debugging convenience callback that gets called immediately after each inference request to the LLM.
     */
    val postRequestToLLMCallback: (context: AIAgentLLMReadSession) -> Unit = { }
) : AgentRuntime, KoinComponent {
    @Transient
    override val transport: McpTransportType = McpTransportType.STREAMABLE_HTTP

    val httpClient by inject<HttpClient>()
    val json by inject<Json>()

    private suspend fun createCoralMcpClient(
        transport: AbstractTransport,
        executionContext: SessionAgentExecutionContext
    ): Client {

        transport.onError { e ->
            if (e !is CancellationException)
                executionContext.logger.error(e) { "Coral MCP error" }
        }

        val client = Client(
            clientInfo = Implementation(
                name = executionContext.registryAgent.name,
                version = executionContext.registryAgent.version
            )
        )
        client.connect(transport)

        return client
    }

    private suspend fun AIAgentFunctionalContext.executeMultipleToolsCatching(
        toolCalls: List<Message.Tool.Call>,
        logger: LoggingInterface
    ): List<ReceivedToolResult> {
        return toolCalls.map {
            try {
                environment.executeTool(it)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val result = e.javaClass.name + ": ${e.message}"
                logger.error(e) { "Got exception while executing tool ${it.tool}: Result is being set as: $result" }

                ReceivedToolResult(
                    it.id,
                    it.tool,
                    toolArgs = JsonObject(emptyMap()),
                    null,
                    result,
                    ToolResultKind.Failure(AIAgentError(e)),
                    null
                )
            }
        }
    }

    private suspend fun AIAgentFunctionalContext.updateSystemResources(client: Client, systemPrompt: String) {
        val newSystemMessage = Message.System(
            injectedWithMcpResources(client, systemPrompt),
            RequestMetaInfo(Clock.System.now())
        )
        return llm.writeSession {
            rewritePrompt { prompt ->
                require(prompt.messages.firstOrNull() is Message.System) { "First message isn't a system message" }
                require(prompt.messages.count { it is Message.System } == 1) { "Not exactly 1 system message" }
                val messagesWithoutSystemMessage = prompt.messages.drop(1)
                val messagesWithNewSystemMessage = listOf(newSystemMessage) + messagesWithoutSystemMessage
                prompt.copy(messages = messagesWithNewSystemMessage)
            }
        }
    }

    private suspend fun injectedWithMcpResources(client: Client, original: String): String {
        val resourceRegex = "<resource>(.*?)</resource>".toRegex()
        val matches = resourceRegex.findAll(original)
        val uris = matches.map { it.groupValues[1] }.toList()
        if (uris.isEmpty()) return original

        val resolvedResources = uris.map { uri ->
            val resource = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri = uri)))
            val contents = resource.contents.joinToString("\n") { (it as TextResourceContents).text }
            "<resource uri=\"$uri\">\n$contents\n</resource>"
        }
        var result = original
        matches.forEachIndexed { index, matchResult ->
            result = result.replace(matchResult.value, resolvedResources[index])
        }
        return result
    }

    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) {
        val mcpUrl = applicationRuntimeContext.getMcpUrl(transport, executionContext, AddressConsumer.LOCAL)
        val coralMcpTransport = transport.getAbstractTransport(httpClient, mcpUrl.toString())

        val coralMcpClient = createCoralMcpClient(coralMcpTransport, executionContext)
        val coralToolRegistry = McpToolRegistryProvider.fromClient(
            mcpClient = coralMcpClient,
            serverInfo = McpServerInfo(url = mcpUrl.toString())
        )

        val resolvedServers = toolServers.map { toolServer -> toolServer.resolve(executionContext) }
        val additionalTools = resolvedServers.flatMap { it.resolvedTools }
        if (toolServers.isNotEmpty())
            executionContext.logger.debug { "Resolved ${additionalTools.size} additional tools from ${toolServers.size} tool servers" }

        val resolvedProxyName = proxyName.resolve(executionContext)
        val proxiedModel = executionContext.graphAgent.proxies[resolvedProxyName]
            ?: throw PrototypeRuntimeException.BadProxy("Proxy \"$resolvedProxyName\" is not a registered proxy request for agent \"${executionContext.registryAgent.identifier}\"")

        var totalTokens = 0L

        val systemPrompt = prompts.system.resolve(executionContext)
        val initialUserMessage = prompts.loop.initial.resolve(executionContext)
        val followupUserMessage = prompts.loop.followup.resolve(executionContext)

        val client = client ?: when (proxiedModel.providerConfig.format) {
            LlmProviderFormat.Anthropic -> PrototypeClient.ANTHROPIC
            LlmProviderFormat.OpenAI -> PrototypeClient.OPEN_AI
        }

        val iterationCount = iterationCount.resolve(executionContext).toInt()
        val iterationDelay = iterationDelay.resolve(executionContext).toInt()

        try {
            AIAgent.Companion(
                systemPrompt = "",
                promptExecutor = client.getPromptExecutor(
                    applicationRuntimeContext.getLlmProxyUrl(
                        executionContext,
                        AddressConsumer.LOCAL,
                        resolvedProxyName
                    ).toString(), executionContext.agent.secret
                ),
                llmModel = client.getLlmModel(proxiedModel),
                toolRegistry = ToolRegistry {
                    tools(coralToolRegistry.tools)
                    tools(additionalTools)
                },
                strategy = functionalStrategy { _: Nothing? ->
                    repeat(iterationCount) { iteration ->
                        try {
                            val iterationTime = measureTime {
                                if (iteration > 0 && iterationDelay > 0) {
                                    executionContext.logger.debug { "Starting iteration $iteration in $iterationDelay ms" }
                                    delay(iterationDelay.milliseconds)
                                } else {
                                    executionContext.logger.debug { "Starting iteration $iteration" }
                                }

                                val resourceUpdateTime = measureTime {
                                    updateSystemResources(coralMcpClient, systemPrompt)
                                }
                                executionContext.logger.debug { "Updated system resources in $resourceUpdateTime" }

                                val (response, llmResponseTime) = measureTimedValue {
                                    requestLLMOnlyCallingTools(if (iteration == 0) initialUserMessage else followupUserMessage)
                                }

                                llm.readSession { readSession -> postRequestToLLMCallback(readSession) }
                                executionContext.logger.debug { "$proxiedModel responded in $llmResponseTime, with: ${response.content}" }

                                val toolCalls = extractToolCalls(listOf(response))
                                executionContext.logger.debug { "Extracted tool calls: ${toolCalls.joinToString { it.tool }}" }

                                val (toolCallResults, toolCallTime) = measureTimedValue {
                                    executeMultipleToolsCatching(toolCalls, executionContext.logger)
                                }
                                executionContext.logger.debug {
                                    "Executed ${toolCallResults.size} tools in $toolCallTime, results: ${
                                        json.encodeToString(
                                            toolCallResults.map { it.toMessage() })
                                    }"
                                }

                                llm.writeSession {
                                    appendPrompt {
                                        tool {
                                            toolCallResults.forEach { toolResult -> this@tool.result(toolResult) }
                                        }
                                    }
                                }
                            }

                            val iterationTokenUsage = latestTokenUsage()
                            totalTokens += iterationTokenUsage
                            executionContext.logger.debug { "Iteration $iteration completed in $iterationTime.  This iteration used $iterationTokenUsage tokens.  Total cumulative token usage is $totalTokens" }

                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (volatile)
                                throw e

                            executionContext.logger.error(e) { "Agent iteration error" }
                        }
                    }
                }
            ).run(null)
        } finally {
            coralMcpClient.close()
            resolvedServers.forEach { it.close() }
        }
    }
}
