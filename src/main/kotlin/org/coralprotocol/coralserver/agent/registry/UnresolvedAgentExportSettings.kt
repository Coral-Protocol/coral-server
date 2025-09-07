package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

typealias UnresolvedAgentExportSettingsMap = Map<RuntimeId, UnresolvedAgentExportSettings>

@Serializable
data class UnresolvedAgentExportSettings(
    val quantity: UInt,
    val pricing: RegistryAgentExportPricing,
    val options: Map<String, AgentOptionValue> = mapOf()
) {
    fun resolve(runtimeId: RuntimeId, agent: RegistryAgent): AgentExportSettings {
        if (quantity == 0u) {
            throw RegistryException("Cannot export 0 \"${agent.info.identifier}\" agents")
        }

        if (agent.runtimes.getById(runtimeId) == null) {
            throw RegistryException("Runtime \"$runtimeId\" is not defined for agent \"${agent.info.identifier}\"")
        }

        return AgentExportSettings(
            quantity = quantity,
            pricing = pricing,
            options = options
        )
    }
}