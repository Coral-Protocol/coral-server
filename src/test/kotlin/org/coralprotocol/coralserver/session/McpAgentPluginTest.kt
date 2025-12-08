package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldThrow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.mcp.tools.optional.CloseSessionInput
import kotlin.test.Test

class McpAgentPluginTest : McpSessionBuilding() {
    @Test
    fun testCommonTools() = sseEnv {
        val agent2Ready = CompletableDeferred<Unit>()

        val (session, _) = sessionManager.createSession("test", AgentGraph(
            agents = mapOf(
                graphAgent(
                    registryAgent = registryAgent(
                        name = "agent1",
                        functionRuntime = client.mcpFunctionRuntime("agent1") { client, _ ->
                            agent2Ready.await()
                            mcpToolManager.closeSessionTool.executeOn(client, CloseSessionInput("Test closure"))
                        }
                    ),
                    provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                    plugins = setOf(GraphAgentPlugin.CloseSessionTool)
                ),
                graphAgent(
                    registryAgent = registryAgent(
                        name = "agent2",
                        functionRuntime = client.mcpFunctionRuntime("agent2") { client, _ ->

                            // agent2 does not have the close session plugin installed
                            shouldThrow<IllegalStateException> {
                                mcpToolManager.closeSessionTool.executeOn(client, CloseSessionInput("Test closure"))
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
