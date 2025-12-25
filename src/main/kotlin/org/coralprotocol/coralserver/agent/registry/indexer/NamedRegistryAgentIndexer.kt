package org.coralprotocol.coralserver.agent.registry.indexer

import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettingsMap
import org.coralprotocol.coralserver.config.RootConfig

data class NamedRegistryAgentIndexer(
    val name: String,
    val indexer: RegistryAgentIndexer
) {
    fun resolveAgent(
        context: RegistryResolutionContext,
        exportSettings: UnresolvedAgentExportSettingsMap,
        agentName: String,
        version: String
    ): RegistryAgent {
        return indexer.resolveAgent(context, exportSettings, name, agentName, version)
    }

    fun update(config: RootConfig) {
        indexer.update(config, name)
    }
}