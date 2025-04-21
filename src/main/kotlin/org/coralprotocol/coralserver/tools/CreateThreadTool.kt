package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.CreateThreadInput
import org.coralprotocol.coralserver.session.session

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the create thread tool to a server.
 */
fun Server.addCreateThreadTool() {
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
                    "creatorId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the agent creating the thread")
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
            required = listOf("threadName", "creatorId", "participantIds")
        )
    ) { request ->
        try {
            // Get the session associated with this server
            val session = this.session
            if (session == null) {
                val errorMessage = "No session associated with this server"
                logger.error { errorMessage }
                return@addTool CallToolResult(
                    content = listOf(TextContent(errorMessage))
                )
            }

            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<CreateThreadInput>(request.arguments.toString())
            val thread = session.createThread(
                name = input.threadName,
                creatorId = input.creatorId,
                participantIds = input.participantIds
            )

            if (thread != null) {
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
            } else {
                val errorMessage = "Failed to create thread: Creator not found or invalid participants"
                logger.error { errorMessage }
                CallToolResult(
                    content = listOf(TextContent(errorMessage))
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error creating thread: ${e.message}"
            logger.error(e) { errorMessage }
            CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    }
}
