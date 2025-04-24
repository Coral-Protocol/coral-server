package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the send message tool to a server.
 */
fun CoralAgentIndividualMcp.addSendMessageTool() {
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
            required = listOf("threadId", "content", "mentions")
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<SendMessageInput>(request.arguments.toString())
            val message = coralAgentGraphSession.sendMessage(
                threadId = input.threadId,
                senderId = this.connectedAgentId,
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
