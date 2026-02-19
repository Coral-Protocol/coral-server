package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.Serializable

@Serializable
data class RegistryAgentMarketplaceSettings(
    @Optional
    val pricing: RegistryAgentMarketplacePricing? = null,

    @Optional
    val identities: RegistryAgentMarketplaceIdentities? = null,
)

@Serializable
data class RegistryAgentMarketplacePricing(
    // markdown
    val description: String,
    val recommendations: RegistryAgentMarketplacePricingRecommendations,

    // todo: a real type
    @Optional
    val currency: String = "USD",
)

@Serializable
data class RegistryAgentMarketplacePricingRecommendations(
    val min: Double,
    val max: Double,
)

@Serializable
data class RegistryAgentMarketplaceIdentities(
    val erc8004: RegistryAgentMarketplaceIdentityErc8004? = null,
)

@Serializable
data class RegistryAgentMarketplaceIdentityErc8004(
    val wallet: String,

    @Optional
    val endpoints: List<Erc8004Endpoint> = listOf()
)

@Serializable
data class Erc8004Endpoint(
    val name: String,
    val endpoint: String,
)