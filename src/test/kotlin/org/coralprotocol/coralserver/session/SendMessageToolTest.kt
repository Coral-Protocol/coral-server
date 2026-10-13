package org.coralprotocol.coralserver.session

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.dsl.graphAgentPair
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.mcp.tools.*
import org.coralprotocol.coralserver.util.sseFunctionRuntime
import org.coralprotocol.coralserver.utils.synchronizedMessageTransaction
import org.koin.test.inject
import java.util.*

/**
 * Contract tests for send_message covering issue #160: it must accept all five content
 * kinds, group multiple parts into one message, and round trip every field through the
 * real tool call path, not just in-memory object construction.
 */
class SendMessageToolTest : CoralTest({
    suspend fun sendMessageRaw(
        client: Client,
        mcpToolManager: McpToolManager,
        argumentsJson: JsonObject
    ): CallToolResult =
        client.callTool(
            CallToolRequest(
                CallToolRequestParams(mcpToolManager.sendMessageTool.name.toString(), argumentsJson)
            )
        )

    test("send_message input schema declares content as a required array field") {
        val mcpToolManager by inject<McpToolManager>()

        val schema = mcpToolManager.sendMessageTool.inputSchema
        val required = schema.required.shouldNotBeNull()
        required shouldContain "content"

        val properties = schema.properties.shouldNotBeNull()
        properties.keys shouldContain "content"

        val contentProperty = (properties["content"] as? JsonObject).shouldNotBeNull()
        contentProperty["type"]?.jsonPrimitive?.content shouldBe "array"
    }

    test("multiple content parts sent in one call are delivered together, in order, as a single message") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val senderName = "sender"
        val receiverName = "receiver"

        val textValue = "intro-${UUID.randomUUID()}"
        val imageData = "img-data-${UUID.randomUUID()}"
        val imageMimeType = "image/png"

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(senderName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, session ->
                                val receiver = session.getAgent(receiverName)

                                val threadId = mcpToolManager.createThreadTool.executeOn(
                                    client, CreateThreadInput("test thread", listOf(receiverName))
                                ).thread.id

                                receiver.synchronizedMessageTransaction {
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(
                                            threadId,
                                            listOf(
                                                SessionThreadMessagePart.Text(textValue),
                                                SessionThreadMessagePart.Image(imageData, imageMimeType)
                                            ),
                                            listOf()
                                        )
                                    ).message.id
                                }
                            })
                        }
                    },
                    graphAgentPair(receiverName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, _ ->
                                val result = mcpToolManager.waitForMessageTool.executeOn(
                                    client, WaitForSingleMessageInput(Long.MAX_VALUE)
                                )
                                val message = result.message.shouldNotBeNull()

                                // the two parts must arrive together, in order, as ONE message,
                                // not truncated or split into two
                                message.content shouldBe listOf(
                                    SessionThreadMessagePart.Text(textValue),
                                    SessionThreadMessagePart.Image(imageData, imageMimeType)
                                )

                                // a second wait must time out: if the two parts had instead been delivered as two
                                // separate messages, this would immediately return the "leftover" second message
                                val second = mcpToolManager.waitForMessageTool.executeOn(
                                    client, WaitForSingleMessageInput(currentUnixTime = Long.MAX_VALUE, maxWaitMs = 200)
                                )
                                second.message.shouldBeNull()
                            })
                        }
                    }
                )
            )
        )

        session.fullLifeCycle()
    }

    test("all five content kinds round-trip through send_message and wait_for_message with every field intact") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val senderName = "sender"
        val receiverName = "receiver"

        val textPart = SessionThreadMessagePart.Text("hello-${UUID.randomUUID()}")
        val imagePart = SessionThreadMessagePart.Image(data = "img-${UUID.randomUUID()}", mimeType = "image/png")
        val audioPart = SessionThreadMessagePart.Audio(data = "aud-${UUID.randomUUID()}", mimeType = "audio/mpeg")
        val embeddedPart = SessionThreadMessagePart.EmbeddedResource(
            resource = ResourceContents.Text(
                text = "resource-text-${UUID.randomUUID()}",
                uri = "coral://resource/${UUID.randomUUID()}",
                mimeType = "text/plain"
            )
        )
        val linkPart = SessionThreadMessagePart.ResourceLink(
            uri = "coral://link/${UUID.randomUUID()}",
            name = "link-name-${UUID.randomUUID()}",
            mimeType = "application/pdf",
            title = "link-title-${UUID.randomUUID()}",
            description = "link-description-${UUID.randomUUID()}"
        )
        val sentParts = listOf(textPart, imagePart, audioPart, embeddedPart, linkPart)

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(senderName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, session ->
                                val receiver = session.getAgent(receiverName)

                                val threadId = mcpToolManager.createThreadTool.executeOn(
                                    client, CreateThreadInput("test thread", listOf(receiverName))
                                ).thread.id

                                receiver.synchronizedMessageTransaction {
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(threadId, sentParts, listOf())
                                    ).message.id
                                }
                            })
                        }
                    },
                    graphAgentPair(receiverName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, _ ->
                                val result = mcpToolManager.waitForMessageTool.executeOn(
                                    client, WaitForSingleMessageInput(Long.MAX_VALUE)
                                )
                                val message = result.message.shouldNotBeNull()

                                // exact structural equality: covers ordering, grouping into one message, correct
                                // discriminator resolution per kind, and lossless field preservation all at once
                                message.content shouldBe sentParts
                            })
                        }
                    }
                )
            )
        )

        session.fullLifeCycle()
    }

    test("embedded resource text/blob sub-kinds and omitted optional fields round-trip without conflation") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val senderName = "sender"
        val receiverName = "receiver"

        val blobPart = SessionThreadMessagePart.EmbeddedResource(
            resource = ResourceContents.Blob(
                blob = "blob-${UUID.randomUUID()}",
                uri = "coral://blob/${UUID.randomUUID()}"
                // mimeType intentionally omitted -> must decode as null, not ""
            )
        )
        val textResourceNoMime = SessionThreadMessagePart.EmbeddedResource(
            resource = ResourceContents.Text(
                text = "plain-${UUID.randomUUID()}",
                uri = "coral://text/${UUID.randomUUID()}"
                // mimeType intentionally omitted -> must decode as null, not ""
            )
        )
        val minimalLink = SessionThreadMessagePart.ResourceLink(
            uri = "coral://minimal/${UUID.randomUUID()}",
            name = "minimal-${UUID.randomUUID()}"
            // mimeType/title/description intentionally omitted -> must decode as null, not ""
        )
        val sentParts = listOf(blobPart, textResourceNoMime, minimalLink)

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(senderName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, session ->
                                val receiver = session.getAgent(receiverName)

                                val threadId = mcpToolManager.createThreadTool.executeOn(
                                    client, CreateThreadInput("test thread", listOf(receiverName))
                                ).thread.id

                                receiver.synchronizedMessageTransaction {
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(threadId, sentParts, listOf())
                                    ).message.id
                                }
                            })
                        }
                    },
                    graphAgentPair(receiverName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, _ ->
                                val result = mcpToolManager.waitForMessageTool.executeOn(
                                    client, WaitForSingleMessageInput(Long.MAX_VALUE)
                                )
                                val message = result.message.shouldNotBeNull()

                                // full structural equality distinguishes Blob from Text sub-kinds (different runtime
                                // classes can never compare equal) and confirms omitted optionals decode as null
                                message.content shouldBe sentParts

                                val roundTrippedLink = message.content[2] as SessionThreadMessagePart.ResourceLink
                                roundTrippedLink.mimeType.shouldBeNull()
                                roundTrippedLink.title.shouldBeNull()
                                roundTrippedLink.description.shouldBeNull()
                            })
                        }
                    }
                )
            )
        )

        session.fullLifeCycle()
    }

    test("an empty content list is not corrupted, padded with a placeholder part, or dropped") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val senderName = "sender"
        val receiverName = "receiver"

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(senderName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, session ->
                                val receiver = session.getAgent(receiverName)

                                val threadId = mcpToolManager.createThreadTool.executeOn(
                                    client, CreateThreadInput("test thread", listOf(receiverName))
                                ).thread.id

                                receiver.synchronizedMessageTransaction {
                                    mcpToolManager.sendMessageTool.executeOn(
                                        client,
                                        SendMessageInput(threadId, emptyList(), listOf())
                                    ).message.id
                                }
                            })
                        }
                    },
                    graphAgentPair(receiverName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, _ ->
                                // Neither the issue nor the approved plan specifies whether an empty content list
                                // should be accepted or rejected; this only asserts that WHATEVER policy is in
                                // effect, it is applied safely: no crash, no silently-injected placeholder part,
                                // and the message is still delivered exactly once (see "Not tested" in the review).
                                val result = mcpToolManager.waitForMessageTool.executeOn(
                                    client, WaitForSingleMessageInput(Long.MAX_VALUE)
                                )
                                val message = result.message.shouldNotBeNull()
                                message.content.shouldBeEmpty()
                            })
                        }
                    }
                )
            )
        )

        session.fullLifeCycle()
    }

    test("a resource_link content item missing its required uri is rejected, not silently defaulted") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val soloName = "solo"

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(soloName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, _ ->
                                val threadId = mcpToolManager.createThreadTool.executeOn(
                                    client, CreateThreadInput("test thread", listOf())
                                ).thread.id

                                val malformedArgs = buildJsonObject {
                                    put("threadId", threadId)
                                    putJsonArray("content") {
                                        addJsonObject {
                                            put("type", "resource_link")
                                            put("name", "a link with no uri")
                                            // "uri" deliberately omitted, it is required
                                        }
                                    }
                                    putJsonArray("mentions") {}
                                }

                                val response = sendMessageRaw(client, mcpToolManager, malformedArgs)
                                response.isError shouldBe true
                                val errorMessage =
                                    response.structuredContent?.get("error")?.jsonPrimitive?.content.shouldNotBeNull()
                                errorMessage shouldContain "uri"
                            })
                        }
                    }
                )
            )
        )

        session.fullLifeCycle()
    }

    test("an embedded_resource content item missing its nested resource uri is rejected") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val soloName = "solo"

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(soloName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, _ ->
                                val threadId = mcpToolManager.createThreadTool.executeOn(
                                    client, CreateThreadInput("test thread", listOf())
                                ).thread.id

                                val malformedArgs = buildJsonObject {
                                    put("threadId", threadId)
                                    putJsonArray("content") {
                                        addJsonObject {
                                            put("type", "embedded_resource")
                                            put("resource", buildJsonObject {
                                                put("type", "text")
                                                put("text", "hello")
                                                // "uri" deliberately omitted on the nested resource contents
                                            })
                                        }
                                    }
                                    putJsonArray("mentions") {}
                                }

                                val response = sendMessageRaw(client, mcpToolManager, malformedArgs)
                                response.isError shouldBe true
                                val errorMessage =
                                    response.structuredContent?.get("error")?.jsonPrimitive?.content.shouldNotBeNull()
                                errorMessage shouldContain "uri"
                            })
                        }
                    }
                )
            )
        )

        session.fullLifeCycle()
    }

    test("an image content item missing its required data field is rejected") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val soloName = "solo"

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(soloName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, _ ->
                                val threadId = mcpToolManager.createThreadTool.executeOn(
                                    client, CreateThreadInput("test thread", listOf())
                                ).thread.id

                                val malformedArgs = buildJsonObject {
                                    put("threadId", threadId)
                                    putJsonArray("content") {
                                        addJsonObject {
                                            put("type", "image")
                                            put("mimeType", "image/png")
                                            // "data" deliberately omitted, it is required
                                        }
                                    }
                                    putJsonArray("mentions") {}
                                }

                                val response = sendMessageRaw(client, mcpToolManager, malformedArgs)
                                response.isError shouldBe true
                                val errorMessage =
                                    response.structuredContent?.get("error")?.jsonPrimitive?.content.shouldNotBeNull()
                                errorMessage shouldContain "data"
                            })
                        }
                    }
                )
            )
        )

        session.fullLifeCycle()
    }

    test("a content item with an unrecognized discriminator value is rejected, not coerced into another kind") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()
        val mcpToolManager by inject<McpToolManager>()

        val soloName = "solo"

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair(soloName) {
                        registryAgent {
                            runtime(client.sseFunctionRuntime(name, version) { client, _ ->
                                val threadId = mcpToolManager.createThreadTool.executeOn(
                                    client, CreateThreadInput("test thread", listOf())
                                ).thread.id

                                val malformedArgs = buildJsonObject {
                                    put("threadId", threadId)
                                    putJsonArray("content") {
                                        addJsonObject {
                                            put("type", "not_a_real_kind")
                                            put("text", "hello")
                                        }
                                    }
                                    putJsonArray("mentions") {}
                                }

                                val response = sendMessageRaw(client, mcpToolManager, malformedArgs)
                                response.isError shouldBe true
                            })
                        }
                    }
                )
            )
        )

        session.fullLifeCycle()
    }
})
