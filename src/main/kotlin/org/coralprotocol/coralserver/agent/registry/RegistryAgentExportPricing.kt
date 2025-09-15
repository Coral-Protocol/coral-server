package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.payment.toMicroCoral
import org.coralprotocol.coralserver.payment.JupiterService

@Serializable
data class RegistryAgentExportPricing(
    @SerialName("min_price")
    val minPrice: AgentClaimAmount,

    @SerialName("max_price")
    val maxPrice: AgentClaimAmount,
) {
    suspend fun withinRange(cost: AgentClaimAmount, jupiterService: JupiterService): Boolean {
        val micro = cost.toMicroCoral(jupiterService)
        return micro in minPrice.toMicroCoral(jupiterService)..maxPrice.toMicroCoral(jupiterService)
    }
}
