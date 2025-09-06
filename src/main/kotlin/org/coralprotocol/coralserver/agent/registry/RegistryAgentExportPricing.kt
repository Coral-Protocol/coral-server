package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegistryAgentExportPricing(
    @SerialName("min_price")
    val minPrice: Double,

    @SerialName("max_price")
    val maxPrice: Double,
)
