package org.coralprotocol.coralserver.agent.registry.reference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentResolutionContext
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent

/**
 * An agent referenced by name and version. This is sourced from a configured indexer.
 */
@Serializable
@SerialName("index")
data class IndexUnresolvedRegistryAgent(
    val name: String,
    val versions: List<String>,
    val indexer: String? = null
) : UnresolvedRegistryAgent() {

    override fun resolve(context: AgentResolutionContext): List<RegistryAgent> {
        return versions.map {
            context
                .registryResolutionContext
                .config
                .registryConfig
                .getIndexer(indexer)
                .resolveAgent(context.registryResolutionContext, mapOf(), name, it)
        }
    }
}