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
import nl.adaptivity.xmlutil.serialization.XML
import org.coralprotocol.coralserver.models.AgentState
import org.coralprotocol.coralserver.models.resolve
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
            properties = buildJsonObject {
                putJsonObject("timeoutMs") {
                    put("type", "number")
                    put("description", "Timeout in milliseconds (default: $maxWaitForMentionsTimeoutMs ms). Must be between 0 and $maxWaitForMentionsTimeoutMs ms.")
                }
            },
            required = listOf("timeoutMs")
        )
    ) { request: CallToolRequest ->
        handleWaitForMentions(request)
    }
}

/**
 * Handles the wait for mentions tool request.
 */
private suspend fun CoralAgentIndividualMcp.handleWaitForMentions(request: CallToolRequest): CallToolResult {
    try {
        val json = Json { ignoreUnknownKeys = true }
        val input = json.decodeFromString<WaitForMentionsInput>(request.arguments.toString())
        logger.info { "Waiting for mentions for agent $connectedAgentId with timeout ${input.timeoutMs}ms" }
        if(input.timeoutMs < 0) {
            return CallToolResult(
                content = listOf(TextContent("Timeout must be greater than 0"))
            )
        }
        if(input.timeoutMs > maxWaitForMentionsTimeoutMs) {
            return CallToolResult(
                content = listOf(TextContent("Timeout must not exceed the maximum of $maxWaitForMentionsTimeoutMs ms"))
            )
        }

        coralAgentGraphSession.setAgentState(agentId = connectedAgentId, state = AgentState.Listening)
        // Use the session to wait for mentions
        val messages = coralAgentGraphSession.waitForMentions(
            agentId = connectedAgentId,
            timeoutMs = input.timeoutMs
        )

        coralAgentGraphSession.setAgentState(agentId = connectedAgentId, state = AgentState.Busy)
        if (messages.isNotEmpty()) {
            logger.info { "Received ${messages.size} messages for agent $connectedAgentId" }
            val formattedMessages = XML.encodeToString (messages.map { message -> message.resolve() })
            return CallToolResult(
                content = listOf(TextContent(formattedMessages))
            )
        } else {
            return CallToolResult(
                content = listOf(TextContent("No new messages received within the timeout period"))
            )
        }
    } catch (e: Exception) {
        val errorMessage = "Error waiting for mentions: ${e.message}"
        logger.error(e) { errorMessage }
        coralAgentGraphSession.setAgentState(agentId = connectedAgentId, state = AgentState.Busy)
        return CallToolResult(
            content = listOf(TextContent(errorMessage))
        )
    }
}
