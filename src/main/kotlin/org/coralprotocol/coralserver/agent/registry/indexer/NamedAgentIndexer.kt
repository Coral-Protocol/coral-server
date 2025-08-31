package org.coralprotocol.coralserver.agent.registry.indexer

import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext
import org.coralprotocol.coralserver.config.Config

data class NamedAgentIndexer(
    val name: String,
    val indexer: AgentIndexer
) {
    fun resolveAgent(
        context: RegistryResolutionContext,
        agentName: String,
        version: String
    ): RegistryAgent {
        return indexer.resolveAgent(context, name, agentName, version)
    }

    fun update(config: Config) {
        indexer.update(config, name)
    }
}