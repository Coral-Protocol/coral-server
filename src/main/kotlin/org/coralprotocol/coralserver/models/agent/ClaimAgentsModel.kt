package org.coralprotocol.coralserver.models.agent

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.remote.RemoteGraphAgentRequest

@Serializable
data class ClaimAgentsModel(
    val agents: List<RemoteGraphAgentRequest>
)