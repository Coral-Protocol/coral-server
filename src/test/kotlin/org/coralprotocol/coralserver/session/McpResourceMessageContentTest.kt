package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.dsl.graphAgentPair
import org.coralprotocol.coralserver.mcp.McpResourceName
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.CreateThreadInput
import org.coralprotocol.coralserver.mcp.tools.SendMessageInput
import org.coralprotocol.coralserver.util.sseFunctionRuntime
import org.koin.test.inject
import java.util.*

/**
 * McpResourceTest never asserts on the messageText field in coral://state. This checks
 * it is populated correctly for single, multiple, and mixed content, and never leaks
 * non-text payload data.
 */
class McpResourceMessageContentTest : CoralTest({
    suspend fun Client.readStateResource(): String {
        val resourceResult =
            readResource(ReadResourceRequest(ReadResourceRequestParams(McpResourceName.STATE_RESOURCE_URI.toString())))
                .shouldNotBeNull()

        val resource = resourceResult.contents.first()
        return resource.shouldBeInstanceOf<TextResourceContents>().text
    }

    test("messageText in coral://state reflects only the Text parts of a sent message") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val agent1Name = "agent1"

        val textOnlyMessage = "unique text only message ${UUID.randomUUID()}"
        val firstPart = "first part ${UUID.randomUUID()}"
        val secondPart = "second part ${UUID.randomUUID()}"
        val mixedMessageText = "text alongside image ${UUID.randomUUID()}"
        val mixedImageCanary = "FAKE_BASE64_IMAGE_DATA_${UUID.randomUUID()}"
        val imageOnlyCanary = "FAKE_BASE64_IMAGE_ONLY_${UUID.randomUUID()}"

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(agent1Name) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, _ ->
                                shouldNotThrowAny {
                                    val thread = mcpToolManager.createThreadTool.executeOn(
                                        client,
                                        CreateThreadInput("content thread", listOf())
                                    ).thread

                                    // 1. plain text message, messageText must carry the exact text sent
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(
                                            thread.id,
                                            listOf(SessionThreadMessagePart.Text(textOnlyMessage)),
                                            listOf()
                                        )
                                    )

                                    var state = client.readStateResource()
                                    state.shouldContain("\"messageText\":\"$textOnlyMessage\"")

                                    // 2. multiple Text parts must be joined, in order, with "\n"
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(
                                            thread.id,
                                            listOf(
                                                SessionThreadMessagePart.Text(firstPart),
                                                SessionThreadMessagePart.Text(secondPart)
                                            ),
                                            listOf()
                                        )
                                    )

                                    state = client.readStateResource()
                                    state.shouldContain("\"messageText\":\"$firstPart\\n$secondPart\"")

                                    // 3. mixed text + non-text content: messageText is just the text, and the
                                    // raw non-text payload must never appear anywhere in the rendered state
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(
                                            thread.id,
                                            listOf(
                                                SessionThreadMessagePart.Text(mixedMessageText),
                                                SessionThreadMessagePart.Image(data = mixedImageCanary, mimeType = "image/png")
                                            ),
                                            listOf()
                                        )
                                    )

                                    state = client.readStateResource()
                                    state.shouldContain("\"messageText\":\"$mixedMessageText\"")
                                    state.shouldNotContain(mixedImageCanary)

                                    // 4. non-text-only content must not crash the resource read, and must
                                    // render an empty summary rather than leaking the raw payload
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(
                                            thread.id,
                                            listOf(SessionThreadMessagePart.Image(data = imageOnlyCanary, mimeType = "image/png")),
                                            listOf()
                                        )
                                    )

                                    lateinit var finalState: String
                                    shouldNotThrowAny {
                                        finalState = client.readStateResource()
                                    }
                                    finalState.shouldContain("\"messageText\":\"\"")
                                    finalState.shouldNotContain(imageOnlyCanary)
                                }
                            })
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        plugin(GraphAgentPlugin.CloseSessionTool)
                    }
                )
            )
        )

        session.fullLifeCycle()
    }
})
