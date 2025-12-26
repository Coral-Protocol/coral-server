@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.indexer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettings
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.RootConfig
import org.koin.core.component.KoinComponent

@Serializable
@JsonClassDiscriminator("type")
sealed interface RegistryAgentIndexer : KoinComponent {
    val priority: Int
    fun resolveAgent(
        context: RegistryResolutionContext,
        exportSettings: Map<RuntimeId, UnresolvedAgentExportSettings>,
        indexerName: String,
        agentName: String,
        version: String
    ): RegistryAgent

    fun update(
        config: RootConfig,
        indexerName: String,
    )
}