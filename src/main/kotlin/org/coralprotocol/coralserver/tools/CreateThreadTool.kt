package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            properties = JsonObject(
                mapOf(
                    "threadName" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Name of the thread")
                        )
                    ),
                    "participantIds" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("array"),
                            "description" to JsonPrimitive("List of agent IDs to include as participants"),
                            "items" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string")
                                )
                            )
                        )
                    )
                )
            ),
            required = listOf("threadName", "participantIds")
        )
    ) { request ->
        try {

            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<CreateThreadInput>(request.arguments.toString())
            val thread = coralAgentGraphSession.createThread(
                name = input.threadName,
                creatorId = connectedAgentId,
                participantIds = input.participantIds
            )

            CallToolResult(
                content = listOf(
                    TextContent(
                        """
                        Thread created successfully:
                        ID: ${thread.id}
                        Name: ${thread.name}
                        Creator: ${thread.creatorId}
                        Participants: ${thread.participants.joinToString(", ")}
                        """.trimIndent()
                    )
                )
            )
        } catch (e: Exception) {
            val errorMessage = "Error creating thread: ${e.message}"
            logger.error(e) { errorMessage }
            CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    }
}
