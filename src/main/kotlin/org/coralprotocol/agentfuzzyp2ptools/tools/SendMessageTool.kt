package org.coralprotocol.agentfuzzyp2ptools.tools

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.agentfuzzyp2ptools.SendMessageInput
import org.coralprotocol.agentfuzzyp2ptools.ThreadManager

/**
 * Extension function to add the send message tool to a server.
 */
fun Server.addSendMessageTool() {
    addTool(
        name = "send_message",
        description = "Send a message to a thread",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "threadId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the thread")
                        )
                    ),
                    "senderId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the agent sending the message")
                        )
                    ),
                    "content" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Content of the message")
                        )
                    ),
                    "mentions" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("array"),
                            "description" to JsonPrimitive("List of agent IDs to mention in the message"),
                            "items" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string")
                                )
                            )
                        )
                    )
                )
            ),
            required = listOf("threadId", "senderId", "content")
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<SendMessageInput>(request.arguments.toString())
            val message = ThreadManager.sendMessage(
                threadId = input.threadId,
                senderId = input.senderId,
                content = input.content,
                mentions = input.mentions
            )

            if (message != null) {
                CallToolResult(
                    content = listOf(
                        TextContent(
                            """
                            Message sent successfully:
                            ID: ${message.id}
                            Thread: ${message.threadId}
                            Sender: ${message.senderId}
                            Content: ${message.content}
                            Mentions: ${message.mentions.joinToString(", ")}
                            """.trimIndent()
                        )
                    )
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("Failed to send message: Thread not found, sender not found, thread is closed, or sender is not a participant"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error sending message: ${e.message}"))
            )
        }
    }
}