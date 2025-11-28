@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.SessionEvent

@Serializable
@JsonClassDiscriminator("type")
sealed interface SocketEvent {
    @Serializable
    @SerialName("debug_agent_registered")
    data class DebugAgentRegistered(val id: String) : SocketEvent

//    @Serializable
//    @SerialName("thread_list")
//    data class ThreadList(val threads: List<ResolvedThread>) : SocketEvent

    @Serializable
    @SerialName("agent_list")
    data class AgentList(val sessionAgents: List<UniqueAgentName>) : SocketEvent

    @Serializable
    @SerialName("session")
    data class Session(val event: SessionEvent) : SocketEvent
}

suspend fun  WebSocketServerSession.sendSocketEvent(event: SocketEvent): Unit =
    send(apiJsonConfig.encodeToString(SocketEvent.serializer(), event))