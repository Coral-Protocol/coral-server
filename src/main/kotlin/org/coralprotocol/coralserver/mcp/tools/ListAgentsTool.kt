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
import org.coralprotocol.coralserver.mcp.McpTooling.LIST_AGENTS_TOOL_NAME
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the list agents tool to a server.
 */
fun CoralAgentIndividualMcp.addListAgentsTool() {
    addTool(
        name = LIST_AGENTS_TOOL_NAME.toString(),
        description = "List all the available Coral agents",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("includeDetails") {
                    put("type", "boolean")
                    put("description", "Whether to include agent details in the response")
                }
            },
            required = listOf("includeDetails")
        )
    ) { request ->
        handleListAgents(request)
    }
}

/**
 * Handles the list agents tool request.
 */
private fun CoralAgentIndividualMcp.handleListAgents(request: CallToolRequest): CallToolResult {
    try {
        val json = Json { ignoreUnknownKeys = true }
        val input = json.decodeFromString<ListAgentsInput>(request.arguments.toString())
        val agents = localSession.getAllAgents()

        if (agents.isNotEmpty()) {
            val agentsList = if (input.includeDetails) {
                agents.joinToString("\n") { agent -> 
                    val description = if (agent.description.isNotEmpty()) {
                        ", Description: ${agent.description}"
                    } else {
                        ""
                    }
                    "ID: ${agent.id}, $description"
                }
            } else {
                agents.joinToString(", ") { agent -> agent.id }
            }

            return CallToolResult(
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
            return CallToolResult(
                content = listOf(TextContent("No agents are currently registered in the system"))
            )
        }
    } catch (e: Exception) {
        val errorMessage = "Error listing agents: ${e.message}"
        logger.error(e) { errorMessage }

        // Return a user-friendly error message
        return CallToolResult(
            content = listOf(TextContent(errorMessage))
        )
    }
}
