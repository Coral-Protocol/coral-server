@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.session.SessionException

@Serializable
data class RestrictedRegistryAgent(
    val registryAgent: RegistryAgent,
    val restrictions: Set<RegistryAgentRestriction> = setOf()
) {
    fun toPublic() = PublicRestrictedRegistryAgent(registryAgent.toPublic(), restrictions)
}

@Serializable
@Description("Represents an agent that can have restrictions on where it can run.")
data class PublicRestrictedRegistryAgent(
    val registryAgent: PublicRegistryAgent,
    val restrictions: Set<RegistryAgentRestriction>
)

@Serializable
@JsonClassDiscriminator("type")
sealed interface RegistryAgentRestriction {
    fun requireNotRestricted(request: GraphAgentRequest)

    @Serializable
    @SerialName("remote")
    @Description("This agent can only be run on remote servers")
    object RemoteOnly : RegistryAgentRestriction {
        override fun requireNotRestricted(request: GraphAgentRequest) {
            if (request.provider !is GraphAgentProvider.RemoteRequest)
                throw SessionException.RestrictedRegistry("Agent ${request.id} may only be run on remote servers")
        }
    }

    @Serializable
    @SerialName("local")
    @Description("This agent can only be run on this server")
    object LocalOnly : RegistryAgentRestriction {
        override fun requireNotRestricted(request: GraphAgentRequest) {
            if (request.provider !is GraphAgentProvider.Local)
                throw SessionException.RestrictedRegistry("Agent ${request.id} may only be run on the local server")
        }
    }

    @Serializable
    @SerialName("linked")
    @Description("This agent can only be run on a specific linked server")
    data class LinkedServerOnly(val linkedServerName: String) : RegistryAgentRestriction {
        override fun requireNotRestricted(request: GraphAgentRequest) {
            if (request.provider !is GraphAgentProvider.Linked || request.provider.linkedServerName != linkedServerName)
                throw SessionException.RestrictedRegistry("Agent ${request.id} may only be run on linked server $linkedServerName")
        }
    }
}