package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.SendMessageInput
import org.coralprotocol.coralserver.session.session

private val logger = KotlinLogging.logger {}

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
            required = listOf("threadId", "senderId", "content", "mentions")
        )
    ) { request ->
        try {
            // Get the session associated with this server
            val session = this.session
            if (session == null) {
                val errorMessage = "No session associated with this server"
                logger.error { errorMessage }
                return@addTool CallToolResult(
                    content = listOf(TextContent(errorMessage))
                )
            }

            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<SendMessageInput>(request.arguments.toString())
            val message = session.sendMessage(
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
                val errorMessage = "Failed to send message: Thread not found, sender not found, thread is closed, or sender is not a participant"
                logger.error { errorMessage }
                CallToolResult(
                    content = listOf(TextContent(errorMessage))
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error sending message: ${e.message}"
            logger.error(e) { errorMessage }
            CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    }
}
