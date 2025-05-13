package org.coralprotocol.coralserver.orchestrator

import org.coralprotocol.coralserver.models.AgentType
import org.coralprotocol.coralserver.models.Provider

data class AgentRegistry(
    val provider: HashMap<AgentType, Provider> = hashMapOf(),
) {
    fun get(agentType: AgentType): Provider {
        return provider[agentType] ?: throw IllegalArgumentException("AgentDefinition $agentType not found")
    }
}