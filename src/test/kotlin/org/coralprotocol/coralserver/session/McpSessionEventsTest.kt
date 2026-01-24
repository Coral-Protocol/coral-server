package org.coralprotocol.coralserver.session

import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.*
import org.coralprotocol.coralserver.util.mcpFunctionRuntime
import org.coralprotocol.coralserver.utils.TestEvent
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.coralprotocol.coralserver.utils.shouldPostEvents
import org.coralprotocol.coralserver.utils.synchronizedMessageTransaction
import org.koin.test.inject
import kotlin.time.Duration.Companion.seconds

class McpSessionEventsTest : CoralTest({
    test("testMcpSessionEvents") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val agent1Name = "agent1"
        val agent2Name = "agent2"
        val agent3Name = "agent3"

        val threadName = "test thread"
        val messageText = "test message"
        val closeSummary = "test thread closed"

        localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(agent1Name) {
                        registryAgent {
                            runtime(client.mcpFunctionRuntime(name, version) { client, session ->
                                session.shouldPostEvents(
                                    timeout = 15.seconds,
                                    allowUnexpectedEvents = true,
                                    events = mutableListOf(
                                        TestEvent("agent wait started") { it is SessionEvent.AgentWaitStart },
                                        TestEvent("agent wait stopped") { it is SessionEvent.AgentWaitStop }
                                    )
                                ) {
                                    mcpToolManager.waitForMessageTool.executeOn(client, WaitForSingleMessageInput)
                                }
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        plugin(GraphAgentPlugin.CloseSessionTool)
                    },
                    graphAgentPair(agent2Name) {
                        registryAgent {
                            runtime(client.mcpFunctionRuntime(name, version) { client, session ->
                                val agent1 = session.getAgent(agent1Name)

                                session.shouldPostEvents(
                                    timeout = 15.seconds,
                                    allowUnexpectedEvents = true,
                                    events = mutableListOf(
                                        TestEvent("thread '$threadName' created") {
                                            it is SessionEvent.ThreadCreated && it.thread.name == threadName
                                        },
                                        TestEvent("message '$messageText' posted") {
                                            it is SessionEvent.ThreadMessageSent && it.message.text == messageText
                                        },
                                        TestEvent("participant '$agent3Name' added to any thread") {
                                            it is SessionEvent.ThreadParticipantAdded && it.name == agent3Name
                                        },
                                        TestEvent("participant '$agent1Name' removed from any thread") {
                                            it is SessionEvent.ThreadParticipantRemoved && it.name == agent1Name
                                        },
                                        TestEvent("any thread closed with summary '$closeSummary'") {
                                            it is SessionEvent.ThreadClosed && it.summary == closeSummary
                                        }
                                    )) {
                                    val thread =
                                        mcpToolManager.createThreadTool.executeOn(
                                            client,
                                            CreateThreadInput(threadName, listOf(agent1Name))
                                        ).thread

                                    agent1.synchronizedMessageTransaction {
                                        mcpToolManager.sendMessageTool.executeOn(
                                            client,
                                            SendMessageInput(thread.id, messageText, listOf())
                                        ).shouldNotBeNull().message.id
                                    }

                                    mcpToolManager.addParticipantTool.executeOn(
                                        client,
                                        AddParticipantInput(thread.id, agent3Name)
                                    )
                                    mcpToolManager.removeParticipantTool.executeOn(
                                        client,
                                        RemoveParticipantInput(thread.id, agent1Name)
                                    )
                                    mcpToolManager.closeThreadTool.executeOn(
                                        client,
                                        CloseThreadInput(thread.id, closeSummary)
                                    )
                                }
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    },
                    graphAgentPair(agent3Name) {
                        registryAgent {
                            runtime(client.mcpFunctionRuntime(name, version) { _, _ ->
                                // nop
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                )
            ))
    }
})