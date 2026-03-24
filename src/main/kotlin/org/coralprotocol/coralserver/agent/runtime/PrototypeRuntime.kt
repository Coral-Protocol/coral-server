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
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
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
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeModelProvider
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypePrompts
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.logging.LoggingInterface
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/*
    todo:
        other mcp
        other mcp auth
 */


@Serializable
@JsonClassDiscriminator("prototype")
@TomlClassDiscriminator("prototype")
data class PrototypeRuntime(
    @SerialName("model")
    val modelProvider: PrototypeModelProvider,

    @SerialName("iterations")
    val iterationCount: Int = 20,

    @SerialName("delay")
    val iterationDelay: Int = 0,

    val prompts: PrototypePrompts = PrototypePrompts(),
    @Transient
    /**
     * Debugging convenience callback that gets called immediately after each inference request to the LLM.
     */
    val postRequestToLLMCallback: (context: AIAgentLLMReadSession) -> Unit = { }
) : AgentRuntime, KoinComponent {

    // TODO: change this back to default when koog fixes streamable http, current latest version 0.7.2 is broken
    @Transient
    override val transport: AgentRuntimeTransport = AgentRuntimeTransport.SSE

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
        val mcpUrl = when (transport) {
            AgentRuntimeTransport.SSE -> applicationRuntimeContext.getSseUrl(executionContext, AddressConsumer.LOCAL)
            AgentRuntimeTransport.STREAMABLE_HTTP -> applicationRuntimeContext.getStreamableHttpUrl(
                executionContext,
                AddressConsumer.LOCAL
            )
        }

        val coralMcpTransport = when (transport) {
            AgentRuntimeTransport.SSE -> SseClientTransport(
                client = httpClient,
                urlString = mcpUrl.toString()
            )

            AgentRuntimeTransport.STREAMABLE_HTTP -> StreamableHttpClientTransport(
                client = httpClient,
                url = mcpUrl.toString()
            )
        }

        val coralMcpClient = createCoralMcpClient(coralMcpTransport, executionContext)
        val coralToolRegistry = McpToolRegistryProvider.fromClient(
            mcpClient = coralMcpClient,
            serverInfo = McpServerInfo(url = mcpUrl.toString())
        )

        val modelIdentifier = modelProvider.getModelIdentifier(executionContext)
        var totalTokens = 0L

        val systemPrompt = prompts.system.resolve(executionContext)
        val initialUserMessage = prompts.loop.initial.resolve(executionContext)
        val followupUserMessage = prompts.loop.followup.resolve(executionContext)

        AIAgent.Companion(
            systemPrompt = "",
            promptExecutor = modelProvider.getExecutor(executionContext),
            llmModel = modelProvider.getModel(executionContext),
            toolRegistry = ToolRegistry {
                tools(coralToolRegistry.tools)
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
                            //
                            llm.readSession { readSession -> postRequestToLLMCallback(readSession) }
                            executionContext.logger.debug { "$modelIdentifier responded in $llmResponseTime, with: ${response.content}" }

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
                        executionContext.logger.error(e) { "Agent iteration error" }
                    }
                }
            }
        ).run(null)
    }
}
