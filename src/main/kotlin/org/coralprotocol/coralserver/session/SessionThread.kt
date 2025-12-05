@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import java.util.*

typealias ThreadId = String

@Serializable
class SessionThread(
    val id: ThreadId = UUID.randomUUID().toString(),
    val name: String,
    val creatorName: UniqueAgentName,
    val participants: MutableSet<UniqueAgentName> = mutableSetOf(),
    val messages: MutableList<SessionThreadMessage> = mutableListOf(),
    var state: SessionThreadState = SessionThreadState.Open
) {
    /**
     * Adds a message to this thread
     *
     * @param message The message to add
     * @param sender The agent that sent the message
     * @param mentions A list of agents that should be mentioned by this message
     *
     * @throws SessionException.ThreadClosedException If this thread state is [SessionThreadState.Closed]
     * @throws SessionException.IllegalThreadMentionException If [sender] is mentioned in [mentions]
     * @throws SessionException.MissingAgentException If any of the agents in [mentions] do not exist in [participants]
     */
    fun addMessage(
        message: String,
        sender: SessionAgent,
        mentions: Set<SessionAgent>
    ): SessionThreadMessage {
        if (state is SessionThreadState.Closed)
            throw SessionException.ThreadClosedException("Cannot send messages to thread ${this.id} because it is closed")

        if (mentions.contains(sender))
            throw SessionException.IllegalThreadMentionException("Messages cannot mention the sender")

        val missing = mentions.filter { !participants.contains(it.name) }
        if (missing.isNotEmpty())
            throw SessionException.MissingAgentException("Cannot mention agents (${missing.joinToString(", ")}}) as they are not participants of thread ${this.id}")

        val msg = SessionThreadMessage(
            text = message,
            senderName = sender.name,
            threadId = this.id,
            mentionNames = mentions.map { it.name }.toSet()
        )
        this.messages.add(msg)

        return msg
    }

    /**
     * Adds an agent to this thread.  The [requestingAgent] must be a participant of this thread.  The agent will
     * receive a notification for each message posted historically to this thread, even if they were previously
     * a participant of the thread.
     *
     * @throws SessionException.AlreadyParticipatingException If [targetAgent] is already participating in this thread
     * @throws SessionException.NotParticipatingException If [requestingAgent] is not participating in this thread
     */
    fun addParticipant(requestingAgent: SessionAgent, targetAgent: SessionAgent) {
        if (!participants.contains(requestingAgent.name))
            throw SessionException.NotParticipatingException("Agent ${requestingAgent.name} is not participating in thread ${this.id}.  Agents must be participants of a thread before they can add others.")

        if (participants.contains(targetAgent.name))
            throw SessionException.AlreadyParticipatingException("Agent ${targetAgent.name} is already participating in thread ${this.id}")

        participants.add(targetAgent.name)
        messages.forEach { targetAgent.notifyMessage(it) }
    }

    /**
     * Removes an agent from this thread.
     *
     * @throws SessionException.NotParticipatingException If neither [requestingAgent] nor [targetAgent] is not participating in this thread
     */
    fun removeParticipant(requestingAgent: SessionAgent, targetAgent: SessionAgent) {
        if (!participants.contains(requestingAgent.name))
            throw SessionException.NotParticipatingException("Agent ${requestingAgent.name} is not participating in thread ${this.id}.  Agents must be participants of a thread before they can remove others.")

        if (!participants.contains(targetAgent.name))
            throw SessionException.NotParticipatingException("Agent ${targetAgent.name} is not participating in thread ${this.id}")

        participants.remove(targetAgent.name)
    }

    /**
     * Transitions this thread to being closed.  All messages in the thread will be deleted, the only remaining data
     * on this thread will be the [summary].
     *
     * @param summary A summary of the thread content previous to its closing.
     */
    fun close(summary: String) {
        if (state is SessionThreadState.Closed)
            throw SessionException.ThreadClosedException("Thread ${this.id} cannot be closed because it is not open")

        state = SessionThreadState.Closed(summary)
        messages.clear()
    }

}

@Serializable
@JsonClassDiscriminator("state")
sealed interface SessionThreadState {
    @Serializable
    @SerialName("open")
    object Open : SessionThreadState

    @Serializable
    @SerialName("closed")
    data class Closed(
        val summary: String
    ) : SessionThreadState
}


