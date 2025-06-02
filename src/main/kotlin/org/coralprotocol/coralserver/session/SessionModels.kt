package org.coralprotocol.coralserver.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime

/**
 * Data class for session creation request.
 */
@Serializable
data class CreateSessionRequest(
    val applicationId: String,
    val sessionId: String?,
    val privacyKey: String,
    val agentGraph: AgentGraphRequest?,
)

@Serializable
data class AgentGraphRequest(
    val agents: HashMap<AgentName, GraphAgentRequest>,
    val links: Set<Set<String>>,
)

@Serializable
sealed interface GraphAgentRequest {
    val options: Map<String, JsonPrimitive>
    val blocking: Boolean?

    @Serializable
    @SerialName("remote")
    data class Remote(val remote: AgentRuntime.Remote, override val options: Map<String, JsonPrimitive> = mapOf(), override val blocking: Boolean? = true) :
        GraphAgentRequest

    @Serializable
    @SerialName("local")
    data class Local(val agentType: AgentType, override val options: Map<String, JsonPrimitive> = mapOf(), override val blocking: Boolean? = true) :
        GraphAgentRequest
}

/**
 * Data class for session creation response.
 */
@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    val applicationId: String,
    val privacyKey: String
)