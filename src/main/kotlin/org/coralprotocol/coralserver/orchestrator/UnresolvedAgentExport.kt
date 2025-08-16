package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.Serializable

@Serializable
data class UnresolvedAgentExport(
    val quantity: UInt
    // todo: pricing here
) {
    fun resolve(name: String, agent: RegistryAgent): AgentExport {
        if (quantity == 0u) {
            throw RegistryException("Cannot export 0 \"$name\" agents")
        }

        return AgentExport(agent, quantity)
    }
}