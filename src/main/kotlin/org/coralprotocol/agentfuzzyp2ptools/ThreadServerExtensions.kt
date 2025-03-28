package org.coralprotocol.agentfuzzyp2ptools

import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.agentfuzzyp2ptools.org.coralprotocol.agentfuzzyp2ptools.ThreadTools

/**
 * Extension function to add all thread-based tools to a server.
 */
fun Server.addThreadTools() {
    addRegisterAgentTool()
    addListAgentsTool()
    addCreateThreadTool()
    addAddParticipantTool()
    addRemoveParticipantTool()
    addCloseThreadTool()
    addSendMessageTool()
    addWaitForMentionsTool()
}

/**
 * Extension function to add the list agents tool to a server.
 */
fun Server.addListAgentsTool() {
    addTool(
        name = "list_agents",
        description = "List all registered agents in the system",
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
            )
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<ListAgentsInput>(request.arguments.toString())
            val agents = ThreadManager.getAllAgents()

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
            CallToolResult(
                content = listOf(TextContent("Error listing agents: ${e.message}"))
            )
        }
    }
}

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
            required = listOf("threadName", "creatorId")
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<CreateThreadInput>(request.arguments.toString())
            val thread = ThreadManager.createThread(
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
                CallToolResult(
                    content = listOf(TextContent("Failed to create thread: Creator not found or invalid participants"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error creating thread: ${e.message}"))
            )
        }
    }
}

/**
 * Extension function to add the add participant tool to a server.
 */
fun Server.addAddParticipantTool() {
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
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<AddParticipantInput>(request.arguments.toString())
            val success = ThreadManager.addParticipant(
                threadId = input.threadId,
                participantId = input.participantId
            )

            if (success) {
                CallToolResult(
                    content = listOf(TextContent("Participant added successfully to thread ${input.threadId}"))
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("Failed to add participant: Thread not found, participant not found, or thread is closed"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error adding participant: ${e.message}"))
            )
        }
    }
}

/**
 * Extension function to add the remove participant tool to a server.
 */
fun Server.addRemoveParticipantTool() {
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
            val success = ThreadManager.removeParticipant(
                threadId = input.threadId,
                participantId = input.participantId
            )

            if (success) {
                CallToolResult(
                    content = listOf(TextContent("Participant removed successfully from thread ${input.threadId}"))
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("Failed to remove participant: Thread not found, participant not found, or thread is closed"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error removing participant: ${e.message}"))
            )
        }
    }
}

/**
 * Extension function to add the close thread tool to a server.
 */
fun Server.addCloseThreadTool() {
    addTool(
        name = "close_thread",
        description = "Close a thread with a summary",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "threadId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the thread to close")
                        )
                    ),
                    "summary" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Summary of the thread")
                        )
                    )
                )
            ),
            required = listOf("threadId", "summary")
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<CloseThreadInput>(request.arguments.toString())
            val success = ThreadManager.closeThread(
                threadId = input.threadId,
                summary = input.summary
            )

            if (success) {
                CallToolResult(
                    content = listOf(TextContent("Thread closed successfully with summary: ${input.summary}"))
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("Failed to close thread: Thread not found"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error closing thread: ${e.message}"))
            )
        }
    }
}

/**
 * Extension function to add the send message tool to a server.
 */
fun Server.addSendMessageTool() {
    addTool(
        name = "send_message",
        description = "Send a message to a thread",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "threadId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the thread")
                        )
                    ),
                    "senderId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the agent sending the message")
                        )
                    ),
                    "content" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("Content of the message")
                        )
                    ),
                    "mentions" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("array"),
                            "description" to JsonPrimitive("List of agent IDs to mention in the message"),
                            "items" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string")
                                )
                            )
                        )
                    )
                )
            ),
            required = listOf("threadId", "senderId", "content")
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<SendMessageInput>(request.arguments.toString())
            val message = ThreadManager.sendMessage(
                threadId = input.threadId,
                senderId = input.senderId,
                content = input.content,
                mentions = input.mentions
            )

            if (message != null) {
                CallToolResult(
                    content = listOf(
                        TextContent(
                            """
                            Message sent successfully:
                            ID: ${message.id}
                            Thread: ${message.threadId}
                            Sender: ${message.senderId}
                            Content: ${message.content}
                            Mentions: ${message.mentions.joinToString(", ")}
                            """.trimIndent()
                        )
                    )
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("Failed to send message: Thread not found, sender not found, thread is closed, or sender is not a participant"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error sending message: ${e.message}"))
            )
        }
    }
}

/**
 * Extension function to add the wait for mentions tool to a server.
 */
fun Server.addWaitForMentionsTool() {
    addTool(
        name = "wait_for_mentions",
        description = "Wait for new messages mentioning an agent, with timeout",
        inputSchema = Tool.Input(
            properties = JsonObject(
                mapOf(
                    "agentId" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "description" to JsonPrimitive("ID of the agent to wait for mentions")
                        )
                    ),
                    "timeoutMs" to JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("number"),
                            "description" to JsonPrimitive("Timeout in milliseconds (default: 30000)")
                        )
                    )
                )
            ),
            required = listOf("agentId")
        )
    ) { request ->
        try {
            val json = Json { ignoreUnknownKeys = true }
            val input = json.decodeFromString<WaitForMentionsInput>(request.arguments.toString())
            println("Waiting for mentions for agent ${input.agentId} with timeout ${input.timeoutMs}ms")
            val messages = ThreadManager.waitForMentions(
                agentId = input.agentId,
                timeoutMs = input.timeoutMs
            )
            if (messages.isNotEmpty()) {
                // Format messages in XML-like structure
                val formattedMessages = ThreadTools.formatMessagesAsXml(messages)
                CallToolResult(
                    content = listOf(TextContent(formattedMessages))
                )
            } else {
                CallToolResult(
                    content = listOf(TextContent("No new messages received within the timeout period"))
                )
            }
        } catch (e: Exception) {
            CallToolResult(
                content = listOf(TextContent("Error waiting for mentions: ${e.message}"))
            )
        }
    }
}
