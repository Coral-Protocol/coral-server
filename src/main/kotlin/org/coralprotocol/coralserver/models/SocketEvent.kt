@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.SessionEvent
import org.coralprotocol.coralserver.session.models.SessionAgent

@Serializable
@JsonClassDiscriminator("type")
sealed interface SocketEvent {
    @Serializable
    @SerialName("debug_agent_registered")
    data class DebugAgentRegistered(val id: String) : SocketEvent

    @Serializable
    @SerialName("thread_list")
    data class ThreadList(val threads: List<ResolvedThread>) : SocketEvent

    @Serializable
    @SerialName("agent_list")
    data class AgentList(val sessionAgents: List<SessionAgent>) : SocketEvent

    @Serializable
    @SerialName("session")
    data class Session(val event: SessionEvent) : SocketEvent
}