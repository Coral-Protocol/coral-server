package org.coralprotocol.coralserver.session.models

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest

@Serializable
data class SessionRequest(
    @Description("A request for the agents in this session")
    val agentGraphRequest: AgentGraphRequest,

    val sessionRuntimeSettings: SessionRuntimeSettings = SessionRuntimeSettings()
)