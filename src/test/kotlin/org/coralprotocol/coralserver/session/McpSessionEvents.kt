package org.coralprotocol.coralserver.session

import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.mcp.tools.*
import kotlin.test.Test

class McpSessionEvents : SessionEvents() {

    @Test
    fun testMcpSessionEvents() = sseEnv {
        val agent1Name = "agent1"
        val agent2Name = "agent2"
        val agent3Name = "agent3"

        buildSession(
            mapOf(
                agent1Name to { client, session ->
                    session.shouldPostEvents(mutableListOf(
                        { it is SessionEvent.AgentWaitStart },
                        { it is SessionEvent.AgentWaitStop }
                    )) {
                        mcpToolManager.waitForMessageTool.executeOn(client, WaitForSingleMessageInput)
                    }
                },
                agent2Name to { client, session ->
                    session.getAgent(agent1Name).waitForWaitState(true)

                    session.shouldPostEvents(mutableListOf(
                        { it is SessionEvent.ThreadCreated && it.thread.name == "test thread" },
                        { it is SessionEvent.ThreadMessageSent && it.message.text == "test message" },
                        { it is SessionEvent.ThreadParticipantAdded && it.name == agent3Name },
                        { it is SessionEvent.ThreadParticipantRemoved && it.name == agent1Name },
                        { it is SessionEvent.ThreadClosed && it.summary == "test thread closed" }
                    )) {
                        val thread =
                            mcpToolManager.createThreadTool.executeOn(client,
                                CreateThreadInput("test thread", setOf(agent1Name))
                            ).thread

                        mcpToolManager.sendMessageTool.executeOn(client,
                            SendMessageInput(thread.id, "test message", setOf())
                        )

                        mcpToolManager.addParticipantTool.executeOn(client, AddParticipantInput(thread.id, agent3Name))
                        mcpToolManager.removeParticipantTool.executeOn(client, RemoveParticipantInput(thread.id, agent1Name))
                        mcpToolManager.closeThreadTool.executeOn(client,
                            CloseThreadInput(thread.id, "test thread closed")
                        )
                    }
                },
                agent3Name to { _, _ ->

                }
            ))
    }
}