package org.coralprotocol.coralserver.agent.registry.reference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentResolutionContext
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import org.coralprotocol.coralserver.config.RegistryConfig
import org.koin.core.component.inject

/**
 * An agent referenced by name and version. This is sourced from a configured indexer.
 */
@Serializable
@SerialName("index")
data class IndexUnresolvedRegistryAgent(
    val name: String,
    val versions: List<String>,
    val indexer: String? = null,
) : UnresolvedRegistryAgent() {
    private val registryConfig: RegistryConfig by inject()

    override fun resolve(context: AgentResolutionContext): List<RegistryAgent> {
        TODO("removing soon")
//        return versions.map {
//            registryConfig
//                .getIndexer(indexer)
//                .resolveAgent(context.registryResolutionContext, unresolvedExportSettings, name, it)
//        }
    }
}