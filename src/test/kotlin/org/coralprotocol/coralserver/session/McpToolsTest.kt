package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.AddParticipantInput
import org.coralprotocol.coralserver.mcp.tools.CreateThreadInput
import org.coralprotocol.coralserver.mcp.tools.SendMessageInput
import org.coralprotocol.coralserver.mcp.tools.WaitForAgentMessageInput
import org.coralprotocol.coralserver.mcp.tools.WaitForMentioningMessageInput
import org.coralprotocol.coralserver.mcp.tools.WaitForSingleMessageInput
import java.util.UUID
import kotlin.test.Test
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.CloseThreadInput
import org.coralprotocol.coralserver.mcp.tools.RemoveParticipantInput
import kotlin.test.assertNotNull

class McpToolsTest : McpSessionBuilding() {
    /**
     * Tool coverage:
     * [McpToolName.CREATE_THREAD]
     * [McpToolName.CLOSE_THREAD]
     * [McpToolName.SEND_MESSAGE]
     * [McpToolName.ADD_PARTICIPANT]
     * [McpToolName.REMOVE_PARTICIPANT]
     * [McpToolName.WAIT_FOR_MESSAGE]
     * [McpToolName.WAIT_FOR_AGENT]
     * [McpToolName.WAIT_FOR_MENTION]
     */
    @Test
    fun testCommonTools() = runTest {
        val toolManager = McpToolManager()

        val singleMessageText = UUID.randomUUID().toString()
        val agentMessageText = UUID.randomUUID().toString()
        val mentionText = UUID.randomUUID().toString()

        val agent1Name = "agent1"
        val agent2Name = "agent2"
        val agent3Name = "agent3"

        buildSession(
            mapOf(
                agent1Name to { client, session ->
                    shouldNotThrowAny {
                        val agent2 = session.getAgent(agent2Name)
                        val agent3 = session.getAgent(agent3Name)

                        val threadName = "test thread"
                        val createThreadResult =
                            toolManager.createThreadTool.executeOn(client, CreateThreadInput(threadName, setOf(agent2Name)))

                        assert(createThreadResult.thread.name == threadName)
                        assert(createThreadResult.thread.creatorName == agent1Name)
                        assert(createThreadResult.thread.participants.contains(agent2Name))

                        // wait for both agent2 and agent3 to enter a waiting state before sending any messages
                        agent2.waitForWaitState(true)
                        agent3.waitForWaitState(true)

                        val sendMessageResult =
                            toolManager.sendMessageTool.executeOn(
                                client,
                                SendMessageInput(createThreadResult.thread.id, singleMessageText, setOf())
                            )

                        agent2.waitForWaitState(false)
                        assert(sendMessageResult.message.text == singleMessageText)
                        assert(sendMessageResult.message.threadId == createThreadResult.thread.id)

                        // wait for agent2 to begin waiting for this message, this time narrowed to just agent1
                        agent2.waitForWaitState(true)
                        toolManager.sendMessageTool.executeOn(client, SendMessageInput(createThreadResult.thread.id, agentMessageText, setOf()))
                        agent2.waitForWaitState(false)

                        // wait for agent2 to begin waiting for a MENTIONING message
                        agent2.waitForWaitState(true)

                        // send a bunch of garbage messages, these should not be picked up by the wait
                        repeat(100) {
                            toolManager.sendMessageTool.executeOn(
                                client,
                                SendMessageInput(createThreadResult.thread.id, "test", setOf())
                            )
                        }

                        // now send the last message, with the mention
                        toolManager.sendMessageTool.executeOn(client, SendMessageInput(createThreadResult.thread.id, mentionText, setOf(agent2Name)))
                        agent2.waitForWaitState(false)

                        toolManager.addParticipantTool.executeOn(client, AddParticipantInput(createThreadResult.thread.id, agent3Name))
                        agent3.waitForWaitState(false)
                    }
                },
                agent2Name to { client, _ ->
                    val singleMessageResult =
                        toolManager.waitForMessageTool.executeOn(client, WaitForSingleMessageInput)
                    assert(singleMessageResult.message?.text == singleMessageText)

                    val agentMessageResult =
                        toolManager.waitForAgentMessageTool.executeOn(client, WaitForAgentMessageInput(agent1Name))
                    assert(agentMessageResult.message?.text == agentMessageText)

                    val mentionResult =
                        toolManager.waitForMentionTool.executeOn(client, WaitForMentioningMessageInput)
                    assert(mentionResult.message?.text == mentionText)
                },
                agent3Name to { client, session ->
                    val agent3 = session.getAgent(agent3Name)

                    val singleMessageResult =
                        toolManager.waitForMessageTool.executeOn(client, WaitForSingleMessageInput).message
                    assertNotNull(singleMessageResult)

                    // the first message that this agent should receive is the first message sent by agent1, but only
                    // after being added to the thread
                    assert(singleMessageResult.text == singleMessageText)
                    assert(agent3.getVisibleMessages().isNotEmpty())

                    toolManager.closeThreadTool.executeOn(client,
                        CloseThreadInput(singleMessageResult.threadId, "Test thread closed")
                    )

                    // thread closed, no messages should be visible anymore
                    assert(agent3.getVisibleMessages().isEmpty())

                    // but the thread should still be visible
                    assert(agent3.getThreads().isNotEmpty())

                    // until agent3 is removed as a participant
                    toolManager.removeParticipantTool.executeOn(client, RemoveParticipantInput(singleMessageResult.threadId, agent3Name))

                    // and now agent3 should have no threads
                    assert(agent3.getThreads().isEmpty())
                }
            ))
    }
}