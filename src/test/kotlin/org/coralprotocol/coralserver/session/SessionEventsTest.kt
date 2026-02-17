package org.coralprotocol.coralserver.session

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.ExecutableRuntime
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.util.sseFunctionRuntime
import org.coralprotocol.coralserver.util.streamableHttpFunctionRuntime
import org.coralprotocol.coralserver.utils.TestEvent
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.coralprotocol.coralserver.utils.shouldPostEvents
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

open class SessionEventsTest : CoralTest({
    suspend fun testSessionEvents(
        runtimeProvider: HttpClient.(
            name: String,
            version: String,
            func: suspend (Client, LocalSession) -> Unit
        ) -> FunctionRuntime
    ) {
        val sessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()

        val agent1Name = "agent1"
        val agent2Name = "agent2"

        val (session, _) = sessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(agent1Name) {
                        registryAgent {
                            FunctionRuntime { executionContext, applicationRuntimeContext ->
                                executionContext.session.shouldPostEvents(
                                    timeout = 10.seconds,
                                    allowUnexpectedEvents = true,
                                    events = mutableListOf(
                                        TestEvent("agent connected") {
                                            it == SessionEvent.AgentConnected(
                                                agent1Name
                                            )
                                        },
                                    )
                                ) {
                                    client.runtimeProvider(name, version) { _, _ ->
                                        // just to trigger AgentConnected
                                    }.execute(executionContext, applicationRuntimeContext)
                                }
                            }
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    },
                    graphAgentPair(agent2Name) {
                        registryAgent {
                            runtime(ExecutableRuntime(listOf("doesn't exist")))
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                )
            ))

        session.shouldPostEvents(
            timeout = 10.seconds,
            allowUnexpectedEvents = true,
            events = mutableListOf(
                TestEvent("agent '$agent1Name' runtime started ") {
                    it == SessionEvent.RuntimeStarted(
                        agent1Name
                    )
                },
                TestEvent("agent '$agent2Name' runtime started") {
                    it == SessionEvent.RuntimeStarted(
                        agent2Name
                    )
                },
                TestEvent("agent '$agent1Name' runtime stopped") {
                    it == SessionEvent.RuntimeStopped(
                        agent1Name
                    )
                },
                TestEvent("agent '$agent2Name' runtime stopped") {
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

    test("testSseSessionEvents").config(timeout = 30.seconds) {
        testSessionEvents(HttpClient::sseFunctionRuntime)
    }

    test("testStreamableHttpSessionEvents").config(timeout = 30.seconds) {
        testSessionEvents(HttpClient::streamableHttpFunctionRuntime)
    }
})