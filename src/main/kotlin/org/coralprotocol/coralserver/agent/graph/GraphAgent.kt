package org.coralprotocol.coralserver.session

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentOptionValue

@Serializable
data class AgentGraph(
    val agents: Map<String, GraphAgent>,
    val tools: Map<String, CustomTool>,
    val links: Set<Set<String>>,
)

@Serializable
data class GraphAgent(
    val name: String,
    val options: Map<String, AgentOptionValue>,
    val systemPrompt: String?,
    val extraTools: Set<String>,
    val blocking: Boolean
)