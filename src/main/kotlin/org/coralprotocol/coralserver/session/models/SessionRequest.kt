package org.coralprotocol.coralserver.session.models

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest

@Serializable
data class SessionRequest(
    @Description("A request for the agents in this session")
    val agentGraphRequest: AgentGraphRequest,

    @Optional
    val sessionRuntimeSettings: SessionRuntimeSettings = SessionRuntimeSettings()
)