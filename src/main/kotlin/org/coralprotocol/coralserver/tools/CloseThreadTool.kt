package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the close thread tool to a server.
 */
fun CoralAgentIndividualMcp.addCloseThreadTool() {
    addTool(
        name = "close_thread",
        description = "Close a thread with a summary",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "threadId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the thread to close")
                        )
                    ),
                    "summary" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Summary of the thread")
                        )
                    )
                )
            ),
            required = listOf("threadId", "summary")
        )
    ) { request ->
        try {

            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<CloseThreadInput>(request.arguments.toString())
            val success = coralAgentGraphSession.closeThread(
                threadId = input.threadId,
                summary = input.summary
            )

            if (success) {
                CallToolResult(
                    content = listOf(TextContent("Thread closed successfully with summary: ${input.summary}"))
                )
            } else {
                val errorMessage = "Failed to close thread: Thread not found"
                logger.error { errorMessage }
                CallToolResult(
                    content = listOf(TextContent(errorMessage))
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error closing thread: ${e.message}"
            logger.error(e) { errorMessage }
            CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    }
}
