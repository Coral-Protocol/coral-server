package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the list agents tool to a server.
 */
fun CoralAgentIndividualMcp.addListAgentsTool() {
    addTool(
        name = "list_agents",
        description = "List all registered agents in your contact.",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "includeDetails" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("boolean"),
                            "description" to JsonPrimitive("Whether to include agent details in the response")
                        )
                    )
                )
            ),
            required = listOf("includeDetails")
        )
    ) { request ->
        try {

            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<ListAgentsInput>(request.arguments.toString())
            val agents = coralAgentGraphSession.getAllAgents()

            if (agents.isNotEmpty()) {
                val agentsList = if (input.includeDetails) {
                    agents.joinToString("\n") { agent -> 
                        val description = if (agent.description.isNotEmpty()) {
                            ", Description: ${agent.description}"
                        } else {
                            ""
                        }
                        "ID: ${agent.id}, Name: ${agent.name}${description}" 
                    }
                } else {
                    agents.joinToString(", ") { agent -> agent.id }
                }

                CallToolResult(
                    content = listOf(
                        TextContent(
                            """
                            Registered Agents (${agents.size}):
                            $agentsList
                            """.trimIndent()
                        )
                    )
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("No agents are currently registered in the system"))
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error listing agents: ${e.message}"
            logger.error(e) { errorMessage }

            // Return a user-friendly error message
            CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    }
}
