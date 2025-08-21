package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

@Serializable
data class AgentExportPricing(
    @SerialName("min_price")
    val minPrice: Double,

    @SerialName("max_price")
    val maxPrice: Double,
)

@Serializable
data class AgentExport(
    val agent: RegistryAgent,
    val runtimes: Map<RuntimeId, AgentExportPricing>,
    val quantity: UInt
)

@Serializable
data class PublicAgentExport(
    val agent: PublicRegistryAgent,
    val runtimes: Map<RuntimeId, AgentExportPricing>,
    val quantity: UInt
)

fun AgentExport.toPublic(id: String): PublicAgentExport {
    return PublicAgentExport(
        agent = agent.toPublic(id),
        runtimes = runtimes,
        quantity = quantity
    )
}