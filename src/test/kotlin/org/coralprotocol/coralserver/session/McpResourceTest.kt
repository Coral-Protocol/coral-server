package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.mcp.McpInstructionSnippet
import org.coralprotocol.coralserver.mcp.McpResourceName
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.CreateThreadInput
import org.coralprotocol.coralserver.mcp.tools.SendMessageInput
import org.coralprotocol.coralserver.util.sseFunctionRuntime
import org.coralprotocol.coralserver.util.streamableHttpFunctionRuntime
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.koin.test.inject

class McpResourceTest : CoralTest({
    suspend fun Client.readResourceByName(name: McpResourceName): String {
        val resourceResult =
            readResource(ReadResourceRequest(ReadResourceRequestParams(name.toString())))
                .shouldNotBeNull()

        val resource = resourceResult.contents.first()
        return resource.shouldBeInstanceOf<TextResourceContents>().text
    }

    suspend fun testStateAndInstructions(
        runtimeProvider: HttpClient.(
            name: String,
            version: String,
            func: suspend (Client, LocalSession) -> Unit
        ) -> FunctionRuntime
    ) {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val agent1Name = "agent1"
        val agent2Name = "agent2"
        val agent3Name = "agent3"

        val threads = MutableStateFlow(0)

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(agent1Name) {
                        registryAgent {
                            runtime(client.runtimeProvider(name, version) { client, _ ->
                                shouldNotThrowAny {
                                    val createThreadResult =
                                        mcpToolManager.createThreadTool.executeOn(
                                            client,
                                            CreateThreadInput("$agent1Name thread", listOf(agent2Name, agent3Name))
                                        )

                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(createThreadResult.thread.id, "test message", listOf())
                                    )

                                    // should include 1 thread and 1 message
                                    val state = client.readResourceByName(McpResourceName.STATE_RESOURCE_URI)
                                    state.shouldContain("\"threadName\":\"$agent1Name thread\"")
                                    state.shouldNotContain("\"threadName\":\"$agent2Name thread\"")
                                    state.shouldNotContain("\"threadName\":\"$agent3Name thread\"")

                                    threads.update { it + 1 }
                                    threads.first { it == 3 }

                                    // quick instructions check also
                                    val instructions =
                                        client.readResourceByName(McpResourceName.INSTRUCTION_RESOURCE_URI)
                                    for (snippet in listOf(
                                        McpInstructionSnippet.BASE,
                                        McpInstructionSnippet.WAITING,
                                        McpInstructionSnippet.MESSAGING
                                    )) {
                                        instructions.shouldContain(snippet.snippet)
                                    }
                                }
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        plugin(GraphAgentPlugin.CloseSessionTool)
                    },
                    graphAgentPair(agent2Name) {
                        registryAgent {
                            runtime(client.runtimeProvider(name, version) { client, _ ->
                                shouldNotThrowAny {
                                    // wait for agent1
                                    threads.first { it == 1 }

                                    val createThreadResult =
                                        mcpToolManager.createThreadTool.executeOn(
                                            client,
                                            CreateThreadInput("$agent2Name thread", listOf(agent1Name, agent3Name))
                                        )

                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(createThreadResult.thread.id, "test message", listOf())
                                    )

                                    // should include output from agent1 and agent2 but not agent3
                                    val state = client.readResourceByName(McpResourceName.STATE_RESOURCE_URI)
                                    state.shouldContain("\"threadName\":\"$agent1Name thread\"")
                                    state.shouldContain("\"threadName\":\"$agent2Name thread\"")
                                    state.shouldNotContain("\"threadName\":\"$agent3Name thread\"")

                                    threads.update { it + 1 }
                                    threads.first { it == 3 }
                                }
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    },
                    graphAgentPair(agent3Name) {
                        registryAgent {
                            runtime(client.runtimeProvider(name, version) { client, _ ->
                                // wait for agent1 and agent2
                                threads.first { it == 2 }

                                val createThreadResult =
                                    mcpToolManager.createThreadTool.executeOn(
                                        client,
                                        CreateThreadInput("$agent3Name thread", listOf(agent1Name, agent2Name))
                                    )

                                mcpToolManager.sendMessageTool.executeOn(
                                    client,
                                    SendMessageInput(createThreadResult.thread.id, "test message", listOf())
                                )

                                // should include all threads
                                val state = client.readResourceByName(McpResourceName.STATE_RESOURCE_URI)
                                state.shouldContain("\"threadName\":\"$agent1Name thread\"")
                                state.shouldContain("\"threadName\":\"$agent2Name thread\"")
                                state.shouldContain("\"threadName\":\"$agent3Name thread\"")

                                threads.update { it + 1 }
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                )
            ))

        session.fullLifeCycle()
    }

    test("testSseStateAndInstructions") {
        testStateAndInstructions(HttpClient::sseFunctionRuntime)
    }

    test("testStreamableHttpStateAndInstructions") {
        testStateAndInstructions(HttpClient::streamableHttpFunctionRuntime)
    }
})