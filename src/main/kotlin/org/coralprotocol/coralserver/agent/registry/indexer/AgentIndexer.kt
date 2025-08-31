@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.indexer

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext
import org.coralprotocol.coralserver.config.Config

@Serializable
@JsonClassDiscriminator("type")
sealed interface AgentIndexer {
    val priority: Int
    fun resolveAgent(
        context: RegistryResolutionContext,
        indexerName: String,
        agentName: String,
        version: String
    ): RegistryAgent

    fun update(
        config: Config,
        indexerName: String,
    )
}