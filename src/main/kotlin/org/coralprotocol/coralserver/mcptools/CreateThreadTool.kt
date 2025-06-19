package org.coralprotocol.coralserver.mcptools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp


private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the create thread tool to a server.
 */
fun CoralAgentIndividualMcp.addCreateThreadTool() {
    addTool(
        name = "create_thread",
        description = "Create a new thread with a list of participants",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadName") {
                    put("type", "string")
                    put("description", "Name of the thread")
                }
                putJsonObject("participantIds") {
                    put("type", "array")
                    put("description", "List of agent IDs to include as participants")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }
            },
            required = listOf("threadName", "participantIds")
        )
    ) { request ->
        handleCreateThread(request)
    }
}

/**
 * Handles the create thread tool request.
 */
private fun CoralAgentIndividualMcp.handleCreateThread(request: CallToolRequest): CallToolResult {
    try {
        val json = Json { ignoreUnknownKeys = true }
        val input = json.decodeFromString<CreateThreadInput>(request.arguments.toString())
        val thread = coralAgentGraphSession.createThread(
            name = input.threadName,
            creatorId = connectedAgentId,
            participantIds = input.participantIds
        )

        return CallToolResult(
            content = listOf(
                TextContent(
                    """
                    |Thread created successfully:
                    |ID: ${thread.id}
                    |Name: ${thread.name}
                    |Creator: ${thread.creatorId}
                    |Participants: ${thread.participants.joinToString(", ")}
                    """.trimMargin()
                )
            )
        )
    } catch (e: Exception) {
        val errorMessage = "Error creating thread: ${e.message}"
        logger.error(e) { errorMessage }
        return CallToolResult(
            content = listOf(TextContent(errorMessage))
        )
    }
}
