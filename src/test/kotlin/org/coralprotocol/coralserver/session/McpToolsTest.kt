package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.coralprotocol.coralserver.mcp.tools.*
import java.util.*

class McpToolsTest : FunSpec({
    test("commonTools") {
        sessionTest {
            val singleMessageText = UUID.randomUUID().toString()
            val agentMessageText = UUID.randomUUID().toString()
            val mentionText = UUID.randomUUID().toString()

            val agent1Name = "agent1"
            val agent2Name = "agent2"
            val agent3Name = "agent3"

            buildSession(
                mapOf(
                    agent1Name to { client, session ->
                        val agent2 = session.getAgent(agent2Name)
                        val agent3 = session.getAgent(agent3Name)

                        val threadName = "test thread"
                        val createThreadResult =
                            mcpToolManager.createThreadTool.executeOn(
                                client,
                                CreateThreadInput(threadName, setOf(agent2Name))
                            )

                        createThreadResult.thread.name shouldBe threadName
                        createThreadResult.thread.creatorName shouldBe agent1Name
                        createThreadResult.thread.hasParticipant(agent2Name) shouldBe true

                        // wait for both agent2 and agent3 to enter a waiting state before sending any messages
                        agent2.synchronizedMessageTransaction {
                            val sendMessageResult = mcpToolManager.sendMessageTool.executeOn(
                                client,
                                SendMessageInput(createThreadResult.thread.id, singleMessageText, setOf())
                            )

                            assert(sendMessageResult.message.text == singleMessageText)
                            assert(sendMessageResult.message.threadId == createThreadResult.thread.id)

                            sendMessageResult.message.id
                        }

                        // wait for agent2 to begin waiting for this message, this time narrowed to just agent1
                        agent2.synchronizedMessageTransaction {
                            mcpToolManager.sendMessageTool.executeOn(
                                client,
                                SendMessageInput(createThreadResult.thread.id, agentMessageText, setOf())
                            ).message.id
                        }

                        agent2.synchronizedMessageTransaction {
                            repeat(100) {
                                // not mentioned, should not be picked up
                                mcpToolManager.sendMessageTool.executeOn(
                                    client,
                                    SendMessageInput(createThreadResult.thread.id, "spam", setOf())
                                )
                            }

                            // does mention, should be picked up
                            mcpToolManager.sendMessageTool.executeOn(
                                client,
                                SendMessageInput(createThreadResult.thread.id, mentionText, setOf(agent2Name))
                            ).message.id
                        }

                        // now send the last message, with the mention
                        agent3.synchronizedMessageTransaction {
                            mcpToolManager.addParticipantTool.executeOn(
                                client,
                                AddParticipantInput(createThreadResult.thread.id, agent3Name)
                            )

                            mcpToolManager.sendMessageTool.executeOn(
                                client,
                                SendMessageInput(createThreadResult.thread.id, mentionText, setOf(agent3Name))
                            ).message.id
                        }
                    },
                    agent2Name to { client, _ ->
                        val singleMessageResult =
                            mcpToolManager.waitForMessageTool.executeOn(client, WaitForSingleMessageInput)
                        singleMessageResult.message?.text shouldBe singleMessageText

                        val agentMessageResult =
                            mcpToolManager.waitForAgentMessageTool.executeOn(
                                client,
                                WaitForAgentMessageInput(agent1Name)
                            )
                        agentMessageResult.message?.text shouldBe agentMessageText

                        val mentionResult =
                            mcpToolManager.waitForMentionTool.executeOn(client, WaitForMentioningMessageInput)
                        mentionResult.message?.text shouldBe mentionText
                    },
                    agent3Name to { client, session ->
                        val agent3 = session.getAgent(agent3Name)

                        val mentionMessageResult =
                            mcpToolManager.waitForMentionTool.executeOn(
                                client,
                                WaitForMentioningMessageInput
                            ).message.shouldNotBeNull()

                        // the first message that this agent should receive is the first message sent by agent1, but only
                        // after being added to the thread
                        mentionMessageResult.text shouldBe mentionText
                        agent3.getVisibleMessages().shouldNotBeEmpty()

                        mcpToolManager.closeThreadTool.executeOn(
                            client,
                            CloseThreadInput(mentionMessageResult.threadId, "Test thread closed")
                        )

                        // thread closed, no messages should be visible anymore
                        agent3.getVisibleMessages().shouldBeEmpty()

                        // but the thread should still be visible
                        agent3.getThreads().shouldNotBeEmpty()

                        // until agent3 is removed as a participant
                        mcpToolManager.removeParticipantTool.executeOn(
                            client,
                            RemoveParticipantInput(mentionMessageResult.threadId, agent3Name)
                        )

                        // and now agent3 should have no threads
                        agent3.getThreads().shouldBeEmpty()
                    }
                ))
        }
    }
})