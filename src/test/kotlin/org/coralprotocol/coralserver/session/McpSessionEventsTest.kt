package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.mcp.tools.*
import kotlin.time.Duration.Companion.seconds

class McpSessionEventsTest : FunSpec({
    test("testMcpSessionEvents") {
        val agent1Name = "agent1"
        val agent2Name = "agent2"
        val agent3Name = "agent3"

        val threadName = "test thread"
        val messageText = "test message"
        val closeSummary = "test thread closed"

        sessionTest {
            buildSession(
                mapOf(
                    agent1Name to { client, session ->
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
                    },
                    agent2Name to { client, session ->
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
                                    CreateThreadInput(threadName, setOf(agent1Name))
                                ).thread

                            agent1.synchronizedMessageTransaction {
                                mcpToolManager.sendMessageTool.executeOn(
                                    client,
                                    SendMessageInput(thread.id, messageText, setOf())
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
                    },
                    agent3Name to { _, _ ->

                    }
                ))
        }
    }
})