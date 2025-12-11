package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.ExecutableRuntime
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.events.SessionEvent
import kotlin.time.Duration.Companion.seconds

open class SessionEventsTest : FunSpec({
    test("testSessionEvents").config(timeout = 2.seconds) {
        val agent1Name = "agent1"
        val agent2Name = "agent2"

        sessionTest {
            val (session, _) = sessionManager.createSession(
                "test", AgentGraph(
                    agents = mapOf(
                        graphAgent(
                            registryAgent = registryAgent(
                                name = agent1Name,
                                functionRuntime = FunctionRuntime { executionContext, applicationRuntimeContext ->
                                    executionContext.session.shouldPostEvents(
                                        3.seconds,
                                        mutableListOf(
                                            ExpectedSessionEvent("agent connected") {
                                                it == SessionEvent.AgentConnected(
                                                    agent1Name
                                                )
                                            },
                                        )
                                    ) {
                                        ktor.client.mcpFunctionRuntime(agent1Name) { _, _ ->
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

            session.shouldPostEvents(
                3.seconds,
                mutableListOf(
                    ExpectedSessionEvent("agent '$agent1Name' runtime started ") {
                        it == SessionEvent.RuntimeStarted(
                            agent1Name
                        )
                    },
                    ExpectedSessionEvent("agent '$agent2Name' runtime started") {
                        it == SessionEvent.RuntimeStarted(
                            agent2Name
                        )
                    },
                    ExpectedSessionEvent("agent '$agent1Name' runtime stopped") {
                        it == SessionEvent.RuntimeStopped(
                            agent1Name
                        )
                    },
                    ExpectedSessionEvent("agent '$agent2Name' runtime stopped") {
                        it == SessionEvent.RuntimeStopped(
                            agent2Name
                        )
                    },
                )
            ) {
                session.launchAgents()
            }

            session.joinAgents()
        }
    }
})