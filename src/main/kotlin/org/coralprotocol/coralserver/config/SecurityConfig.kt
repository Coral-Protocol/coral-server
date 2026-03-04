package org.coralprotocol.coralserver.config

data class SecurityConfig(
    /**
     * If this is false, coral-agent.toml files imported from Git, agent indexers or local paths will not be allowed to
     * contain an export section.  It is recommended to keep this value set to false unless you have a good reason to
     * set it to true and understand the risks involved.
     */
    val enableReferencedExporting: Boolean = false,
)