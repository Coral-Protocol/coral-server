package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

typealias AgentExportSettingsMap = Map<RuntimeId, AgentExportSettings>
typealias PublicAgentExportSettingsMap = Map<RuntimeId, PublicAgentExportSettings>

@Serializable
data class AgentExportSettings(
    val quantity: UInt,
    val pricing: RegistryAgentExportPricing,
    val options: Map<String, AgentOptionValue> = mapOf(),
)

@Serializable
data class PublicAgentExportSettings(
    val quantity: UInt,
    val pricing: RegistryAgentExportPricing
)

fun AgentExportSettings.toPublic(): PublicAgentExportSettings {
    return PublicAgentExportSettings(
        quantity = quantity,
        pricing = pricing
    )
}

fun AgentExportSettingsMap.toPublic(): PublicAgentExportSettingsMap {
    return mapValues { (runtime, settings) ->
        settings.toPublic()
    }
}