package org.coralprotocol.agentfuzzyp2ptools.tools

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.agentfuzzyp2ptools.WaitForMentionsInput
import org.coralprotocol.agentfuzzyp2ptools.ThreadManager
import org.coralprotocol.agentfuzzyp2ptools.ThreadTools

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the wait for mentions tool to a server.
 */
fun Server.addWaitForMentionsTool() {
    addTool(
        name = "wait_for_mentions",
        description = "Wait for new messages mentioning an agent, with timeout",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "agentId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the agent to wait for mentions")
                        )
                    ),
                    "timeoutMs" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("number"),
                            "description" to JsonPrimitive("Timeout in milliseconds (default: 30000)")
                        )
                    )
                )
            ),
            required = listOf("agentId")
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<WaitForMentionsInput>(request.arguments.toString())
            logger.info { "Waiting for mentions for agent ${input.agentId} with timeout ${input.timeoutMs}ms" }
            val messages = ThreadManager.waitForMentions(
                agentId = input.agentId,
                timeoutMs = input.timeoutMs
            )
            if (messages.isNotEmpty()) {
                // Format messages in XML-like structure
                val formattedMessages = ThreadTools.formatMessagesAsXml(messages)
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
