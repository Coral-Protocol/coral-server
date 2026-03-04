package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldThrow
import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.mcp.McpToolException
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.optional.CloseSessionInput
import org.coralprotocol.coralserver.util.sseFunctionRuntime
import org.coralprotocol.coralserver.util.streamableHttpFunctionRuntime
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.koin.test.inject

class McpAgentPluginTest : CoralTest({
    suspend fun testCloseSessionTool(
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

        val agent2Ready = CompletableDeferred<Unit>()

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(agent1Name) {
                        registryAgent {
                            runtime(client.runtimeProvider(name, version) { client, _ ->
                                agent2Ready.await()
                                mcpToolManager.closeSessionTool.executeOn(client, CloseSessionInput("Test closure"))
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        plugin(GraphAgentPlugin.CloseSessionTool)
                    },
                    graphAgentPair(agent2Name) {
                        registryAgent {
                            runtime(client.runtimeProvider(name, version) { client, _ ->
                                // agent2 does not have the close session plugin installed
                                shouldThrow<McpToolException> {
                                    mcpToolManager.closeSessionTool.executeOn(
                                        client,
                                        CloseSessionInput("Test closure")
                                    )
                                }

                                agent2Ready.complete(Unit)

                                // agent1 should close the session, cancelling this coroutine
                                delay(1000)
                                throw AssertionError("session should close before this exception is thrown")
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                )
            ))

        session.fullLifeCycle()
    }

    test("testSseCloseSessionTool") {
        testCloseSessionTool(HttpClient::sseFunctionRuntime)
    }

    test("testStreamableHttpCloseSessionTool") {
        testCloseSessionTool(HttpClient::streamableHttpFunctionRuntime)
    }
})