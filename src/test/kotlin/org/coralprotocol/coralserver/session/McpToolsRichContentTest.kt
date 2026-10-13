package org.coralprotocol.coralserver.session

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.dsl.graphAgentPair
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.*
import org.coralprotocol.coralserver.util.sseFunctionRuntime
import org.coralprotocol.coralserver.util.streamableHttpFunctionRuntime
import org.coralprotocol.coralserver.utils.synchronizedMessageTransaction
import org.koin.test.inject
import java.util.*

/**
 * McpToolsTest only sends a single Text part over real MCP transports. This proves every
 * SessionThreadMessagePart variant, plus a multi-part message, survives a real send and
 * receive round trip over both SSE and Streamable HTTP.
 */
class McpToolsRichContentTest : CoralTest({
    suspend fun testRichContent(
        runtimeProvider: HttpClient.(
            name: String,
            version: String,
            func: suspend (Client, LocalSession) -> Unit
        ) -> FunctionRuntime
    ) {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val agent1Name = "agent1"
        val agent2Name = "agent2"

        // One message covering all five variants, both ResourceContents shapes, and both
        // a fully populated and a fully default ResourceLink.
        val richContent = listOf(
            SessionThreadMessagePart.Text(
                text = "rich-text-${UUID.randomUUID()}"
            ),
            SessionThreadMessagePart.Image(
                data = "aGVsbG8td29ybGQtaW1hZ2U=",
                mimeType = "image/png"
            ),
            SessionThreadMessagePart.Audio(
                data = "aGVsbG8td29ybGQtYXVkaW8=",
                mimeType = "audio/wav"
            ),
            SessionThreadMessagePart.ResourceLink(
                uri = "https://example.com/resource/${UUID.randomUUID()}",
                name = "full-resource-link-${UUID.randomUUID()}",
                mimeType = "application/pdf",
                title = "A resource link with every field populated",
                description = "Exercises the non-null path of every optional ResourceLink field"
            ),
            SessionThreadMessagePart.ResourceLink(
                uri = "https://example.com/resource/${UUID.randomUUID()}",
                name = "minimal-resource-link-${UUID.randomUUID()}"
                // mimeType, title, description intentionally left at their null default: with this server's
                // Json config (explicitNulls = false), these are omitted on the wire entirely, exercising the
                // decode-time default path rather than an explicit JSON null.
            ),
            SessionThreadMessagePart.EmbeddedResource(
                resource = ResourceContents.Text(
                    text = "embedded-text-${UUID.randomUUID()}",
                    uri = "coral://embedded-text/${UUID.randomUUID()}",
                    mimeType = "text/plain"
                )
            ),
            SessionThreadMessagePart.EmbeddedResource(
                resource = ResourceContents.Blob(
                    blob = "ZW1iZWRkZWQtYmluYXJ5LWRhdGE=",
                    uri = "coral://embedded-blob/${UUID.randomUUID()}",
                    mimeType = "application/octet-stream"
                )
            )
        )

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(agent1Name) {
                        registryAgent {
                            runtime(client.runtimeProvider(name, version) { client, session ->
                                val agent2 = session.getAgent(agent2Name)

                                val threadName = "rich content thread"
                                val createThreadResult =
                                    mcpToolManager.createThreadTool.executeOn(
                                        client,
                                        CreateThreadInput(threadName, listOf(agent2Name))
                                    )

                                agent2.synchronizedMessageTransaction {
                                    val sendMessageResult = mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(createThreadResult.thread.id, richContent, listOf())
                                    )

                                    // Round trip #1: this process encoded `richContent` into the SendMessageInput
                                    // JSON-RPC request, sent it over the real transport, the server decoded it,
                                    // stored it, re-encoded the resulting SessionThreadMessage into the
                                    // SendMessageOutput JSON-RPC response, and this process decoded it back.
                                    sendMessageResult.message.content shouldBe richContent
                                    sendMessageResult.message.threadId shouldBe createThreadResult.thread.id
                                    sendMessageResult.message.senderName shouldBe agent1Name

                                    sendMessageResult.message.id
                                }
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    },
                    graphAgentPair(agent2Name) {
                        registryAgent {
                            runtime(client.runtimeProvider(name, version) { client, _ ->
                                val waitResult =
                                    mcpToolManager.waitForMessageTool.executeOn(client, WaitForSingleMessageInput(Long.MAX_VALUE))

                                val message = waitResult.message.shouldNotBeNull()

                                // A second, independent client receives the stored message via
                                // wait_for_message, proving a fresh decode, not just an echo.
                                message.content shouldBe richContent
                                message.senderName shouldBe agent1Name
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                )
            ))

        session.fullLifeCycle()
    }

    test("testSseRichContent") {
        testRichContent(HttpClient::sseFunctionRuntime)
    }

    test("testStreamableHttpRichContent") {
        testRichContent(HttpClient::streamableHttpFunctionRuntime)
    }
})
