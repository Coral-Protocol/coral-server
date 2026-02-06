package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.Serializable

@Serializable
data class RegistryAgentMarketplaceSettings(
    // markdown
    val shortDescription: String,

    @Optional
    val links: Map<String, String> = mapOf(),

    @Optional
    val license: String? = null,

    @Optional
    val pricing: RegistryAgentMarketplacePricing? = null,

    @Optional
    val identities: RegistryAgentMarketplaceIdentities? = null,
)

@Serializable
data class RegistryAgentMarketplacePricing(
    // markdown
    val description: String,

    // todo: ISO 4217? crypto?
    val currency: String,

    val recommendedMinPrice: Double,
    val recommendedMaxPrice: Double,
)

@Serializable
data class RegistryAgentMarketplaceIdentities(
    val erc8004: RegistryAgentMarketplaceIdentityErc8004? = null,
)

@Serializable
data class RegistryAgentMarketplaceIdentityErc8004(
    val wallet: String,
    val endpoints: List<Erc8004Endpoint>
)

@Serializable
data class Erc8004Endpoint(
    val name: String,
    val endpoint: String,
)