@file:OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)

package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.util.InstantSerializer
import org.coralprotocol.coralserver.util.utcTimeNow
import org.koin.core.component.KoinComponent
import kotlin.time.ExperimentalTime

/**
 * Identifies where an agent's registry entry came from.  The runtime trust tier applied to the agent is a function
 * of this value: `Local` is treated as trusted; `Marketplace` and `Linked` are treated as untrusted and run under the
 * marketplace docker hardening profile.  The authoritative mapping lives in
 * `org.coralprotocol.coralserver.agent.execution.resolveTrustPolicy`.
 */
@Serializable
@JsonClassDiscriminator("type")
@Description("Where an agent's registry entry came from. Local is trusted; Marketplace and Linked are untrusted (run under the marketplace docker hardening profile).")
sealed class AgentRegistrySourceIdentifier {
    @Serializable
    @SerialName("local")
    object Local : AgentRegistrySourceIdentifier()

    @Serializable
    @SerialName("marketplace")
    object Marketplace : AgentRegistrySourceIdentifier()

    @Serializable
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

/**
 * This cannot be an abstract class.  Kotlinx serialization will try to serialize this as a polymorphic type if it is
 * either abstract or an interface, in this case we only want this base class to be what is serialized regardless of
 * implementation.
 */
@Serializable
open class AgentRegistrySource(val identifier: AgentRegistrySourceIdentifier) : KoinComponent {
    @Suppress("unused")
    @Serializable(with = InstantSerializer::class)
    val timestamp = utcTimeNow()

    open val name: String = "default"

    /**
     * All agents that are available in this registry agent source
     */
    open val agents: MutableList<RegistryAgentCatalog> = mutableListOf()

    /**
     * @see [AgentRegistry.resolveAgent]
     */
    open suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent {
        throw RegistryException.AgentNotFoundException("Agent ${agent.name} not found in registry")
    }
}