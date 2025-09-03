package org.coralprotocol.coralserver.mcp.tools

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

const val CLOSE_THREAD_TOOL_NAME = "coral_close_thread"

/**
 * Extension function to add the close thread tool to a server.
 */
fun CoralAgentIndividualMcp.addCloseThreadTool() {
    addTool(
        name = CLOSE_THREAD_TOOL_NAME,
        description = "Closes a Coral thread with a summary",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadId") {
                    put("type", "string")
                    put("description", "ID of the thread to close")
                }
                putJsonObject("summary") {
                    put("type", "string")
                    put("description", "Summary of the thread")
                }
            },
            required = listOf("threadId", "summary")
        )
    ) { request ->
        handleCloseThread(request)
    }
}

 /**
 * Handles the close thread tool request.
 */
private fun CoralAgentIndividualMcp.handleCloseThread(request: CallToolRequest): CallToolResult {
    try {
        val json = Json { ignoreUnknownKeys = true }
        val input = json.decodeFromString<CloseThreadInput>(request.arguments.toString())
        val success = localSession.closeThread(
            threadId = input.threadId,
            summary = input.summary
        )

        if (success) {
            return CallToolResult(
                content = listOf(TextContent("Thread closed successfully with summary: ${input.summary}"))
            )
        } else {
            val errorMessage = "Failed to close thread: Thread not found"
            logger.error { errorMessage }
            return CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    } catch (e: Exception) {
        val errorMessage = "Error closing thread: ${e.message}"
        logger.error(e) { errorMessage }
        return CallToolResult(
            content = listOf(TextContent(errorMessage))
        )
    }
}
