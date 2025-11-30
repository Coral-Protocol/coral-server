@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.SessionThread
import org.coralprotocol.coralserver.session.SessionThreadMessage

/**
 * Events used in [LocalSession.events]
 */
@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionEvent {
    @Serializable
    @SerialName("runtime_started")
    data class RuntimeStarted(val name: UniqueAgentName) : SessionEvent

    @Serializable
    @SerialName("runtime_stopped")
    data class RuntimeStopped(val name: UniqueAgentName) : SessionEvent

    @Serializable
    @SerialName("agent_connected")
    data class AgentConnected(val name: UniqueAgentName) : SessionEvent

    @Serializable
    @SerialName("thread_created")
    data class ThreadCreated(val thread: SessionThread) : SessionEvent

    @Serializable
    @SerialName("thread_closed")
    data class ThreadClosed(val thread: SessionThread) : SessionEvent

    @Serializable
    @SerialName("message_sent")
    data class MessageSent(val message: SessionThreadMessage) : SessionEvent
}