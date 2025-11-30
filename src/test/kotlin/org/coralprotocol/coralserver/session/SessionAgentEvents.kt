package org.coralprotocol.coralserver.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.ExecutableRuntime
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.events.AgentEvent
import kotlin.test.Test

class SessionAgentEvents : SessionBuilding() {
    @Test
    fun testAgentEvents() = sseEnv {
        withContext(Dispatchers.IO) {
            val (session, _) = sessionManager.createSession("test", AgentGraph(
                agents = mapOf(
                    "agent1" to graphAgent(
                        registryAgent = registryAgent(
                            functionRuntime = FunctionRuntime { _, _ ->

                            }
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    ),
                    "agent2" to graphAgent(
                        registryAgent = registryAgent(
                            executableRuntime = ExecutableRuntime(listOf("doesn't exist"))
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.EXECUTABLE)
                    ),
                ),
                customTools = mapOf(),
                groups = setOf()
            ))

            val agent1Events = mutableListOf<AgentEvent>()
            val agent2Events = mutableListOf<AgentEvent>()
            session.sessionScope.launch { session.getAgent("agent1").events.toList(agent1Events) }
            session.sessionScope.launch { session.getAgent("agent2").events.toList(agent2Events) }

            session.launchAgents()
            session.waitForAgents()

            assert(agent1Events.any { it is AgentEvent.RuntimeStarted })
            assert(agent1Events.any { it is AgentEvent.RuntimeStopped })
            assert(agent2Events.any { it is AgentEvent.RuntimeStarted })
            assert(agent2Events.any { it is AgentEvent.RuntimeStopped })
        }
    }
}