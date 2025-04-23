package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the add participant tool to a server.
 */
fun CoralAgentIndividualMcp.addAddParticipantTool() {
    addTool(
        name = "add_participant",
        description = "Add a participant to a thread",
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
                            "description" to JsonPrimitive("ID of the agent to add")
                        )
                    )
                )
            ),
            required = listOf("threadId", "participantId")
        )
    ) { request ->
        try {
            // Get the session associated with this server

            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<AddParticipantInput>(request.arguments.toString())
            val success = coralAgentGraphSession.addParticipantToThread(
                threadId = input.threadId,
                participantId = input.participantId
            )

            if (success) {
                CallToolResult(
                    content = listOf(TextContent("Participant added successfully to thread ${input.threadId}"))
                )
            } else {
                val errorMessage = "Failed to add participant: Thread not found, participant not found, or thread is closed"
                logger.error { errorMessage }
                CallToolResult(
                    content = listOf(TextContent(errorMessage))
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error adding participant: ${e.message}"
            logger.error(e) { errorMessage }
            CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    }
}
