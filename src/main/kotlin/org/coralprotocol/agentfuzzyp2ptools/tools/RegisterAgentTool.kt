package org.coralprotocol.agentfuzzyp2ptools.tools

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.agentfuzzyp2ptools.Agent
import org.coralprotocol.agentfuzzyp2ptools.RegisterAgentInput
import org.coralprotocol.agentfuzzyp2ptools.session.session

private val logger = KotlinLogging.logger {}

/**
 * Extension function to add the register agent tool to a server.
 */
fun Server.addRegisterAgentTool() {
    addTool(
        name = "register_agent",
        description = "Register an agent in the system for discovery by other agents. Only register yourself, and make sure to register yourself once before using other tools.",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "agentId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Unique, descriptive identifier for the agent. DO NOT CALL YOURSELF SOMETHING GENERIC LIKE 'ASSISTANT' OR 'AGENT'.")
                        )
                    ),
                    "agentName" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Display name for the agent")
                        )
                    ),
                    "description" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Description of the agent's responsibilities")
                        )
                    )
                )
            ),
            required = listOf("agentId", "agentName", "description")
        )
    ) { request: CallToolRequest ->
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
            val input = json.decodeFromString<RegisterAgentInput>(request.arguments.toString())
            val agent = Agent(id = input.agentId, name = input.agentName, description = input.description)

            // Use the session to register the agent
            val success = session.registerAgent(agent)

            // Check if we need to wait for a specific number of agents
            if (session.waitForAgents > 0) {
                logger.info { "Waiting for ${session.waitForAgents} agents to register before returning..." }

                // Wait for the specified number of agents with a timeout
                val timeout = 60000L
                val waitResult = session.waitForAgentCount(session.waitForAgents, timeout)

                if (waitResult) {
                    logger.info { "Successfully waited for ${session.waitForAgents} agents to register" }
                } else {
                    logger.warn { "Timed out waiting for ${session.waitForAgents} agents to register. Current count: ${session.getRegisteredAgentsCount()}" }
                }
            }

            if (success) {
                val descriptionInfo = if (agent.description.isNotEmpty()) {
                    "\nDescription: ${agent.description}"
                } else {
                    ""
                }
                logger.info { "Agent registered successfully: ${agent.name} (${agent.id})${descriptionInfo}" }
                CallToolResult(
                    content = listOf(TextContent("Agent registered successfully: ${agent.name} (${agent.id})${descriptionInfo}"))
                )

            } else {
                val errorMessage = "Failed to register agent: Agent ID '${agent.id}' already exists"
                logger.error { errorMessage }

                CallToolResult(
                    content = listOf(TextContent(errorMessage))
                )
            }
        } catch (e: Exception) {
            val errorMessage = "Error registering agent: ${e.message}"
            logger.error(e) { errorMessage }

            CallToolResult(
                content = listOf(TextContent(errorMessage))
            )
        }
    }
}
