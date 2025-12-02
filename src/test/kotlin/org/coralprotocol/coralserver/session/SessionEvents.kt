package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.ExecutableRuntime
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.events.SessionEvent
import kotlin.test.Test

class SessionEvents : SessionBuilding() {
    @Test
    fun testAgentEvents() = sseEnv {
        withContext(Dispatchers.IO) {
            val (session, _) = sessionManager.createSession("test", AgentGraph(
                agents = mapOf(
                    graphAgent(
                        registryAgent = registryAgent(
                            name = "agent1",
                            functionRuntime = FunctionRuntime { _, _ ->

                            }
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    ),
                    graphAgent(
                        registryAgent = registryAgent(
                            name = "agent2",
                            executableRuntime = ExecutableRuntime(listOf("doesn't exist"))
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.EXECUTABLE)
                    ),
                ),
                customTools = mapOf(),
                groups = setOf()
            ))

            val collecting = CompletableDeferred<Unit>()
            val events = mutableListOf<SessionEvent>()
            session.sessionScope.launch {
                collecting.complete(Unit)
                session.events.toList(events)
            }

            collecting.await()
            session.launchAgents()
            session.joinAgents()

            assert(events.any { it is SessionEvent.RuntimeStarted && it.name == "agent1" })
            assert(events.any { it is SessionEvent.RuntimeStarted && it.name == "agent2" })
            assert(events.any { it is SessionEvent.RuntimeStopped && it.name == "agent1" })
            assert(events.any { it is SessionEvent.RuntimeStopped && it.name == "agent2" })
        }
    }
}