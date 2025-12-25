package org.coralprotocol.coralserver.config

data class RegistryConfig(
    /**
     * A list of paths to registry files.
     */
    val localRegistries: List<String> = listOf(),

    /**
     * If this is true, all debug agents will be included in the registry
     */
    val includeDebugAgents: Boolean = false,

    /**
     * If this is true and [includeDebugAgents] is true, the debug agents included will also be exported
     */
    val exportDebugAgents: Boolean = false,

    /**
     * If this is true, the entire marketplace will be used as a potential agent registry source.
     */
    val enableMarketplaceAgentRegistrySource: Boolean = false,
)