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
import org.coralprotocol.coralserver.session.SessionThreadMessageFilter
import org.coralprotocol.coralserver.session.ThreadId

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
    @SerialName("agent_wait_start")
    data class AgentWaitStart(val name: UniqueAgentName, val filters: Set<SessionThreadMessageFilter>) : SessionEvent

    @Serializable
    @SerialName("agent_wait_stop")
    data class AgentWaitStop(val name: UniqueAgentName, val message: SessionThreadMessage) : SessionEvent

    @Serializable
    @SerialName("thread_created")
    data class ThreadCreated(val thread: SessionThread) : SessionEvent

    @Serializable
    @SerialName("thread_participant_added")
    data class ThreadParticipantAdded(val threadId: ThreadId, val name: UniqueAgentName) : SessionEvent

    @Serializable
    @SerialName("thread_participant_removed")
    data class ThreadParticipantRemoved(val threadId: ThreadId, val name: UniqueAgentName) : SessionEvent

    @Serializable
    @SerialName("thread_closed")
    data class ThreadClosed(val threadId: ThreadId, val summary: String) : SessionEvent

    @Serializable
    @SerialName("thread_message_sent")
    data class ThreadMessageSent(val message: SessionThreadMessage) : SessionEvent

    @Serializable
    @SerialName("docker_container_created")
    data class DockerContainerCreated(val containerId: String) : SessionEvent

    @Serializable
    @SerialName("docker_container_removed")
    data class DockerContainerRemoved(val containerId: String) : SessionEvent
}