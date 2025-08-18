package org.coralprotocol.coralserver.session

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentOptionValue

@JvmInline
@Serializable
value class AgentName(private val name: String) {
    override fun toString() = name
}

@Serializable
data class AgentGraph(
    val agents: Map<AgentName, GraphAgent>,
    val tools: Map<String, CustomTool>,
    val links: Set<Set<String>>,
)

sealed interface GraphAgent {
    val options: Map<String, AgentOptionValue>
    val systemPrompt: String?
    val extraTools: Set<String>
    val blocking: Boolean

    data class Remote(
        val remote: org.coralprotocol.coralserver.agent.runtime.Remote,
        override val extraTools: Set<String> = setOf(),
        override val systemPrompt: String? = null,
        override val options: Map<String, AgentOptionValue> = mapOf(),
        override val blocking: Boolean = true
    ) : GraphAgent

    data class Local(
        val agentType: String,
        override val extraTools: Set<String> = setOf(),
        override val systemPrompt: String? = null,
        override val options: Map<String, AgentOptionValue> = mapOf(),
        override val blocking: Boolean = true
    ) : GraphAgent
}