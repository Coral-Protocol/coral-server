package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class AgentType(private val type: String)

@Serializable
data class AgentRegistry(
    val agents: Map<AgentType, RegistryAgent> = mapOf(),
) {
    fun get(agentType: AgentType): RegistryAgent {
        return agents[agentType] ?: throw IllegalArgumentException("AgentDefinition $agentType not found")
    }
}