package org.coralprotocol.coralserver.config

import java.nio.file.Path

data class RegistryConfig(
    /**
     * A list of agents available on the file system to add as local agents to this server.  This supports basic pattern
     * matching.
     */
    val localAgents: List<String> = listOf(
        "${Path.of(System.getProperty("user.home"), ".coral", "agents")}/*"
    ),

    /**
     * If this is true, a file watcher will be installed for [localAgents] which will monitor:
     * - new potential matches for given patterns
     * - changes to matched agents
     * - deletion of agents
     */
    val watchLocalAgents: Boolean = true,

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