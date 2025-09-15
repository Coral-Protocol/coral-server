@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.models.ResolvedMessage
import org.coralprotocol.coralserver.session.models.SessionAgent
import org.coralprotocol.coralserver.session.models.SessionAgentState

@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionEvent {
    @Serializable
    @SerialName("agent_registered")
    data class AgentRegistered(val agent: SessionAgent) : SessionEvent

    @Serializable
    @SerialName("agent_state_updated")
    data class AgentStateUpdated(val agentId: String, val state: SessionAgentState): SessionEvent

    @Serializable
    @SerialName("agent_ready")
    data class AgentReady(val agent: String): SessionEvent

    @Serializable
    @SerialName("thread_created")
    data class ThreadCreated(val id: String, val name: String, val creatorId: String, val participants: List<String>, val summary: String?): SessionEvent

    @Serializable
    @SerialName("message_sent")
    data class MessageSent(val threadId: String, val message: ResolvedMessage): SessionEvent
}