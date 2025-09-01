package org.coralprotocol.coralserver.agent.registry.reference

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext

/**
 * An agent referenced by name and version. This is sourced from a configured indexer.
 */
@Serializable
data class NameReference(
    val version: String,
    val indexer: String?
) : UnresolvedRegistryAgentReference {
    override fun resolve(
        context: RegistryResolutionContext,
        name: String
    ): RegistryAgent {
        return context
            .config
            .registryConfig
            .getIndexer(indexer)
            .resolveAgent(context, name, version)
    }
}