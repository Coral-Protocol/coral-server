package org.coralprotocol.coralserver.mcptools

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
            properties = buildJsonObject {
                putJsonObject("threadId") {
                    put("type", "string")
                    put("description", "ID of the thread")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "Content of the message")
                }
                putJsonObject("mentions") {
                    put("type", "array")
                    put("description", "List of agent IDs to mention in the message. You *must* mention an agent for them to be made aware of the message.")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }
            },
            required = listOf("threadId", "content", "mentions")
        )
    ) { request ->
        handleSendMessage(request)
    }
}

/**
 * Handles the send message tool request.
 */
private suspend fun CoralAgentIndividualMcp.handleSendMessage(request: CallToolRequest): CallToolResult {
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
            logger.info { message }

            return CallToolResult(
                content = listOf(
                    TextContent(
                        """
                        Message sent successfully:
                        ID: ${message.id}
                        Thread: ${message.thread.id}
                        Sender: ${message.sender.id}
                        Content: ${message.content}
                        Mentions: ${message.mentions.joinToString(", ")}
                        """.trimIndent()
                    )
                )
            )
        } else {
            val errorMessage = "Failed to send message: Thread not found, sender not found, thread is closed, or sender is not a participant"
            logger.error { errorMessage }
            return CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    } catch (e: Exception) {
        val errorMessage = "Error sending message: ${e.message}"
        logger.error(e) { errorMessage }
        return CallToolResult(
            content = listOf(TextContent(errorMessage))
        )
    }
}
