package org.coralprotocol.coralserver.session

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.ConfigValue
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime

@JvmInline
@Serializable
value class AgentName(private val name: String)

@Serializable
data class AgentGraph(
    val agents: Map<AgentName, GraphAgent>,
    val links: Set<Set<String>>,
)

sealed interface GraphAgent {
    val options: Map<String, ConfigValue>

    data class Remote(val remote: AgentRuntime.Remote, override val options: Map<String, ConfigValue> = mapOf()) :
        GraphAgent

    data class Local(val agentType: AgentType, override val options: Map<String, ConfigValue> = mapOf()) :
        GraphAgent
}