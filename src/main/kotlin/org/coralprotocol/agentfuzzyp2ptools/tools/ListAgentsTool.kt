package org.coralprotocol.agentfuzzyp2ptools.tools

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.agentfuzzyp2ptools.ListAgentsInput
import org.coralprotocol.agentfuzzyp2ptools.session.session

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the list agents tool to a server.
 */
fun Server.addListAgentsTool() {
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
            val input = json.decodeFromString<ListAgentsInput>(request.arguments.toString())
            val agents = session.getAllAgents()

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
