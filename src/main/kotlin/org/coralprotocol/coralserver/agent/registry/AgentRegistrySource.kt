@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed class AgentRegistrySourceIdentifier {
    @SerialName("local")
    object Local : AgentRegistrySourceIdentifier()

    @SerialName("marketplace")
    object Marketplace : AgentRegistrySourceIdentifier()

    @SerialName("linked")
    data class Linked(val linkedServerId: String) : AgentRegistrySourceIdentifier()

    override fun toString(): String {
        return when (this) {
            is Linked -> "linked($linkedServerId)"
            is Local -> "local"
            Marketplace -> "marketplace"
        }
    }
}

@Serializable
abstract class AgentRegistrySource(val identifier: AgentRegistrySourceIdentifier) {
    val timestamp: Long = System.currentTimeMillis()

    /**
     * All agents that are available in this registry agent source
     */
    abstract val agents: List<RegistryAgentCatalog>

    /**
     * @see [AgentRegistry.resolveAgent]
     */
    abstract suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent
}