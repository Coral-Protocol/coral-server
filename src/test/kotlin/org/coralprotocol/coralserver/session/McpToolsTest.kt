package org.coralprotocol.coralserver.session

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.*
import org.coralprotocol.coralserver.util.mcpFunctionRuntime
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.coralprotocol.coralserver.utils.synchronizedMessageTransaction
import org.koin.test.inject
import java.util.*

class McpToolsTest : CoralTest({
    test("commonTools") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val singleMessageText = UUID.randomUUID().toString()
        val agentMessageText = UUID.randomUUID().toString()
        val mentionText = UUID.randomUUID().toString()

        val agent1Name = "agent1"
        val agent2Name = "agent2"
        val agent3Name = "agent3"

        localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(agent1Name) {
                        registryAgent {
                            runtime(client.mcpFunctionRuntime(name, version) { client, session ->
                                val agent2 = session.getAgent(agent2Name)
                                val agent3 = session.getAgent(agent3Name)

                                val threadName = "test thread"
                                val createThreadResult =
                                    mcpToolManager.createThreadTool.executeOn(
                                        client,
                                        CreateThreadInput(threadName, listOf(agent2Name))
                                    )

                                createThreadResult.thread.name shouldBe threadName
                                createThreadResult.thread.creatorName shouldBe agent1Name
                                createThreadResult.thread.hasParticipant(agent2Name) shouldBe true

                                // wait for both agent2 and agent3 to enter a waiting state before sending any messages
                                agent2.synchronizedMessageTransaction {
                                    val sendMessageResult = mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(createThreadResult.thread.id, singleMessageText, listOf())
                                    )

                                    assert(sendMessageResult.message.text == singleMessageText)
                                    assert(sendMessageResult.message.threadId == createThreadResult.thread.id)

                                    sendMessageResult.message.id
                                }

                                // wait for agent2 to begin waiting for this message, this time narrowed to just agent1
                                agent2.synchronizedMessageTransaction {
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(createThreadResult.thread.id, agentMessageText, listOf())
                                    ).message.id
                                }

                                agent2.synchronizedMessageTransaction {
                                    repeat(100) {
                                        // not mentioned, should not be picked up
                                        mcpToolManager.sendMessageTool.executeOn(
                                            client,
                                            SendMessageInput(createThreadResult.thread.id, "spam", listOf())
                                        )
                                    }

                                    // does mention, should be picked up
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(createThreadResult.thread.id, mentionText, listOf(agent2Name))
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
                                        SendMessageInput(createThreadResult.thread.id, mentionText, listOf(agent3Name))
                                    ).message.id
                                }
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        plugin(GraphAgentPlugin.CloseSessionTool)
                    },
                    graphAgentPair(agent2Name) {
                        registryAgent {
                            runtime(client.mcpFunctionRuntime(name, version) { client, session ->
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
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    },
                    graphAgentPair(agent3Name) {
                        registryAgent {
                            runtime(client.mcpFunctionRuntime(name, version) { client, session ->
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
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                )
            ))
    }
})