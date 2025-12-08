package org.coralprotocol.coralserver.session

import kotlinx.coroutines.*
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.ExecutableRuntime
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.events.SessionEvent
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

open class SessionEvents : McpSessionBuilding() {
    suspend fun LocalSession.shouldPostEvents(expectedEvents: MutableList<(event: SessionEvent) -> Boolean>, block: suspend () -> Unit) {
        val listening = CompletableDeferred<Unit>()
        val eventJob = sessionScope.launch {
            listening.complete(Unit)

            events.collect { event ->
                expectedEvents.removeAll { it(event) }

                if (expectedEvents.isEmpty())
                    cancel()
            }
        }

        val blockJob = sessionScope.launch {
            listening.await()
            block()
        }

        withTimeoutOrNull(5.seconds) {
            joinAll(eventJob, blockJob)
        } ?: throw AssertionError("timeout waiting for events ${expectedEvents.size} more events")
    }

    @Test
    fun testRuntimeEvents() = sseEnv {
        val (session, _) = sessionManager.createSession("test", AgentGraph(
            agents = mapOf(
                graphAgent(
                    registryAgent = registryAgent(
                        name = "agent1",
                        functionRuntime = FunctionRuntime { executionContext, applicationRuntimeContext ->
                            executionContext.session.shouldPostEvents(mutableListOf(
                                { it == SessionEvent.AgentConnected("agent1") },
                            )) {
                                client.mcpFunctionRuntime("agent1") { _, _ ->
                                    // just to trigger AgentConnected
                                }.execute(executionContext, applicationRuntimeContext)
                            }
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

        session.shouldPostEvents(mutableListOf(
            { it == SessionEvent.RuntimeStarted("agent1") },
            { it == SessionEvent.RuntimeStarted("agent2") },
            { it == SessionEvent.RuntimeStopped("agent1") },
            { it == SessionEvent.RuntimeStopped("agent1") },
        )) {
            session.launchAgents()
        }

        session.joinAgents()
    }
}