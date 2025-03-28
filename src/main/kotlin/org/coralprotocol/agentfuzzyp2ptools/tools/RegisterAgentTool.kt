package org.coralprotocol.agentfuzzyp2ptools.tools

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.agentfuzzyp2ptools.Agent
import org.coralprotocol.agentfuzzyp2ptools.RegisterAgentInput
import org.coralprotocol.agentfuzzyp2ptools.ThreadManager

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
            required = listOf("agentId", "agentName")
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<RegisterAgentInput>(request.arguments.toString())
            val agent = Agent(id = input.agentId, name = input.agentName, description = input.description)
            val success = ThreadManager.registerAgent(agent)

            if (success) {
                val descriptionInfo = if (agent.description.isNotEmpty()) {
                    "\nDescription: ${agent.description}"
                } else {
                    ""
                }
                println("Agent registered successfully: ${agent.name} (${agent.id})${descriptionInfo}")
                CallToolResult(
                    content = listOf(TextContent("Agent registered successfully: ${agent.name} (${agent.id})${descriptionInfo}"))
                )

            } else {
                CallToolResult(
                    content = listOf(TextContent("Failed to register agent: Agent ID already exists"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error registering agent: ${e.message}"))
            )
        }
    }
}