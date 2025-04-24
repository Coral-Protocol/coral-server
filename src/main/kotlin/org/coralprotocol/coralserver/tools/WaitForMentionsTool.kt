package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.ThreadTools
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the wait for mentions tool to a server.
 */
fun CoralAgentIndividualMcp.addWaitForMentionsTool() {
    addTool(
        name = "wait_for_mentions",
        description = "Wait until mentioned. Call this tool when you're done or want to wait for another agent to respond. This will block until a message is received. You will see all unread messages.",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "timeoutMs" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("number"),
                            "description" to JsonPrimitive("Timeout in milliseconds (default: 30000)")
                        )
                    )
                )
            ),
            required = listOf("timeoutMs")
        )
    ) { request: CallToolRequest ->

        try {

            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<WaitForMentionsInput>(request.arguments.toString())
            logger.info { "Waiting for mentions for agent ${connectedAgentId} with timeout ${input.timeoutMs}ms" }

            // Use the session to wait for mentions
            val messages = coralAgentGraphSession.waitForMentions(
                agentId = connectedAgentId,
                timeoutMs = input.timeoutMs
            )

            if (messages.isNotEmpty()) {
                // Format messages in XML-like structure using the session
                val formattedMessages = ThreadTools.formatMessagesAsXml(messages, coralAgentGraphSession)
                CallToolResult(
                    content = listOf(TextContent(formattedMessages))
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("No new messages received within the timeout period"))
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error waiting for mentions: ${e.message}"
            logger.error(e) { errorMessage }
            CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    }
}
