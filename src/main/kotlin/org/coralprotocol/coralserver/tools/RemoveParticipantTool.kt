package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the remove participant tool to a server.
 */
fun CoralAgentIndividualMcp.addRemoveParticipantTool() {
    addTool(
        name = "remove_participant",
        description = "Remove a participant from a thread",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "threadId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the thread")
                        )
                    ),
                    "participantId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the agent to remove")
                        )
                    )
                )
            ),
            required = listOf("threadId", "participantId")
        )
    ) { request ->
        try {

            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<RemoveParticipantInput>(request.arguments.toString())
            val success = coralAgentGraphSession.removeParticipantFromThread(
                threadId = input.threadId,
                participantId = input.participantId
            )

            if (success) {
                CallToolResult(
                    content = listOf(TextContent("Participant removed successfully from thread ${input.threadId}"))
                )
            } else {
                val errorMessage = "Failed to remove participant: Thread not found, participant not found, or thread is closed"
                logger.error { errorMessage }
                CallToolResult(
                    content = listOf(TextContent(errorMessage))
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error removing participant: ${e.message}"
            logger.error(e) { errorMessage }
            CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    }
}
