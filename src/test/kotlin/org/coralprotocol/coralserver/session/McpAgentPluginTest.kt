package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.mcp.tools.optional.CloseSessionInput
import org.coralprotocol.coralserver.util.mcpFunctionRuntime

class McpAgentPluginTest : FunSpec({
    test("testCloseSessionTool") {
        sessionTest {
            val agent1Name = "agent1"
            val agent2Name = "agent2"

            val agent2Ready = CompletableDeferred<Unit>()

            val (session, _) = sessionManager.createSession(
                "test", AgentGraph(
                    agents = mapOf(
                        graphAgent(
                            registryAgent = registryAgent(
                                name = agent1Name,
                                functionRuntime = ktor.client.mcpFunctionRuntime(agent1Name, "1.0.0") { client, _ ->
                                    agent2Ready.await()
                                    mcpToolManager.closeSessionTool.executeOn(client, CloseSessionInput("Test closure"))
                                }
                            ),
                            provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                            plugins = setOf(GraphAgentPlugin.CloseSessionTool)
                        ),
                        graphAgent(
                            registryAgent = registryAgent(
                                name = agent2Name,
                                functionRuntime = ktor.client.mcpFunctionRuntime(agent2Name, "1.0.0") { client, _ ->

                                    // agent2 does not have the close session plugin installed
                                    shouldThrow<IllegalStateException> {
                                        mcpToolManager.closeSessionTool.executeOn(
                                            client,
                                            CloseSessionInput("Test closure")
                                        )
                                    }

                                    agent2Ready.complete(Unit)

                                    // agent1 should close the session, cancelling this coroutine
                                    delay(1000)
                                    throw AssertionError("session should close before this exception is thrown")
                                }
                            ),
                            provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        ),
                    ),
                    customTools = mapOf(),
                    groups = setOf()
                ))

            session.launchAgents()
            session.joinAgents()
        }
    }
})