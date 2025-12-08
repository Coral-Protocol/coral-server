package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.coralprotocol.coralserver.mcp.McpInstructionSnippet
import org.coralprotocol.coralserver.mcp.McpResourceName
import org.coralprotocol.coralserver.mcp.tools.CreateThreadInput
import org.coralprotocol.coralserver.mcp.tools.SendMessageInput
import kotlin.test.Test
import kotlin.test.assertNotNull

class McpResourceTest : McpSessionBuilding() {
    private suspend fun Client.readResourceByName(name: McpResourceName): String {
        val resourceResult =
            readResource(ReadResourceRequest(name.toString()))
        assertNotNull(resourceResult)

        val resource = resourceResult.contents.first()
        require(resource is TextResourceContents)
        return resource.text
    }

    /**
     * A proper test here would involve using LLMs to performance check the states/resources.  For now this test will
     * simply make sure the resources are available and reactive.
     */
    @Test
    fun testCommonTools() = runTest {
        val agent1Name = "agent1"
        val agent2Name = "agent2"
        val agent3Name = "agent3"

        val threads = MutableStateFlow(0)

        buildSession(
            mapOf(
                agent1Name to { client, _ ->
                    shouldNotThrowAny {
                        val createThreadResult =
                            mcpToolManager.createThreadTool.executeOn(client, CreateThreadInput("$agent1Name thread", setOf(agent2Name, agent3Name)))

                        mcpToolManager.sendMessageTool.executeOn(client, SendMessageInput(createThreadResult.thread.id, "test message", setOf()))

                        // should include 1 thread and 1 message
                        val state = client.readResourceByName(McpResourceName.STATE_RESOURCE_URI)
                        assert(state.contains("\"threadName\":\"$agent1Name thread\""))
                        assert(!state.contains("\"threadName\":\"$agent2Name thread\""))
                        assert(!state.contains("\"threadName\":\"$agent3Name thread\""))

                        threads.update { it + 1 }
                        threads.first { it == 3 }

                        // quick instructions check also
                        val instructions = client.readResourceByName(McpResourceName.INSTRUCTION_RESOURCE_URI)
                        for (snippet in listOf(McpInstructionSnippet.BASE, McpInstructionSnippet.WAITING, McpInstructionSnippet.MESSAGING)) {
                            assert(instructions.contains(snippet.snippet))
                        }
                    }
                },
                agent2Name to { client, _ ->
                    shouldNotThrowAny {
                        // wait for agent1
                        threads.first { it == 1 }

                        val createThreadResult =
                            mcpToolManager.createThreadTool.executeOn(client, CreateThreadInput("$agent2Name thread", setOf(agent1Name, agent3Name)))

                        mcpToolManager.sendMessageTool.executeOn(client, SendMessageInput(createThreadResult.thread.id, "test message", setOf()))

                        // should include output from agent1 and agent2 but not agent3
                        val state = client.readResourceByName(McpResourceName.STATE_RESOURCE_URI)
                        assert(state.contains("\"threadName\":\"$agent1Name thread\""))
                        assert(state.contains("\"threadName\":\"$agent2Name thread\""))
                        assert(!state.contains("\"threadName\":\"$agent3Name thread\""))

                        threads.update { it + 1 }
                        threads.first { it == 3 }
                    }
                },
                agent3Name to { client, _ ->
                    shouldNotThrowAny {
                        // wait for agent1 and agent2
                        threads.first { it == 2 }

                        val createThreadResult =
                            mcpToolManager.createThreadTool.executeOn(client, CreateThreadInput("$agent3Name thread", setOf(agent1Name, agent2Name)))

                        mcpToolManager.sendMessageTool.executeOn(client, SendMessageInput(createThreadResult.thread.id, "test message", setOf()))

                        // should include all threads
                        val state = client.readResourceByName(McpResourceName.STATE_RESOURCE_URI)
                        assert(state.contains("\"threadName\":\"$agent1Name thread\""))
                        assert(state.contains("\"threadName\":\"$agent2Name thread\""))
                        assert(state.contains("\"threadName\":\"$agent3Name thread\""))

                        threads.update { it + 1 }
                    }
                }
            ))
    }
}