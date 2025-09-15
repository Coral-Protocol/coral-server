package org.coralprotocol.coralserver.session.models

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest

@Serializable
data class SessionRequest(
    @Description("A unique identifier for the application that requested this session")
    val applicationId: String,

    @Description("Optionally set the session's ID.  If not set, a random UUID will be used")
    val sessionId: String? = null,

    @Description("For future use")
    val privacyKey: String,

    @Description("A request for a graph of agents")
    val agentGraphRequest: AgentGraphRequest
)