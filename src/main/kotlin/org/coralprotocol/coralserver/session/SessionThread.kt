@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.events.SessionEvent
import java.util.*

typealias ThreadId = String

@Serializable
class SessionThread(
    val id: ThreadId = UUID.randomUUID().toString(),
    val name: String,
    val creatorName: UniqueAgentName,
    private val participants: MutableSet<UniqueAgentName> = mutableSetOf(),
    private val messages: MutableList<SessionThreadMessage> = mutableListOf(),
    var state: SessionThreadState = SessionThreadState.Open
) {
    @Transient
    private val participantsMutex = Mutex()

    @Transient
    private val messagesMutex = Mutex()

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
    suspend fun addMessage(
        message: String,
        sender: SessionAgent,
        mentions: Set<SessionAgent>
    ): SessionThreadMessage {
        val state = state
        if (state is SessionThreadState.Closed) {
            sender.logger.warn { "tried to send message into thread $id which was closed with summary \"${state.summary}\"" }
            throw SessionException.ThreadClosedException("Cannot send messages to thread ${this.id} because it is closed")
        }

        if (mentions.contains(sender)) {
            sender.logger.warn { "tried to send message into thread $id that mentioned myself" }
            throw SessionException.IllegalThreadMentionException("Messages cannot mention the sender")
        }

        participantsMutex.withLock {
            val missing = mentions.filter { !participants.contains(it.name) }
            if (missing.isNotEmpty()) {
                sender.logger.warn {
                    "tried to send message into thread $id that mentioned the following non-participating agents: ${
                        missing.joinToString(
                            ", "
                        )
                    }"
                }

                throw SessionException.MissingAgentException("Cannot mention agents (${missing.joinToString(", "){ it.name }}) as they are not participants of thread ${this.id}")
            }
        }

        val msg = SessionThreadMessage(
            text = message,
            senderName = sender.name,
            threadId = this.id,
            mentionNames = mentions.map { it.name }.toSet()
        )
        messagesMutex.withLock { messages.add(msg) }

        // notify participating agents
        participantsMutex.withLock {
            participants.forEach {
                sender.session.getAgent(it).notifyMessage(msg)
            }
        }

        sender.session.events.emit(SessionEvent.ThreadMessageSent(msg))

        val mentionLogStr = if (mentions.isEmpty()) {
            " with no mentions"
        } else {
            ", mentioning: ${mentions.joinToString(", ") { it.name }}"
        }

        sender.logger.info { "sent message \"${message}\" (id=${msg.id}) into thread $id$mentionLogStr" }
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
    suspend fun addParticipant(requestingAgent: SessionAgent, targetAgent: SessionAgent) {
        participantsMutex.withLock {
            if (!participants.contains(requestingAgent.name)) {
                requestingAgent.logger.warn { "tried to add \"${targetAgent.name}\" to thread $id which ${requestingAgent.name} is not a participant of" }
                throw SessionException.NotParticipatingException("Agent ${requestingAgent.name} is not participating in thread ${this.id}.  Agents must be participants of a thread before they can add others.")
            }

            if (participants.contains(targetAgent.name)) {
                requestingAgent.logger.warn { "tried to add already-participating agent \"${targetAgent.name}\" to thread $id" }
                throw SessionException.AlreadyParticipatingException("Agent ${targetAgent.name} is already participating in thread ${this.id}")
            }

            participants.add(targetAgent.name)
        }

        messagesMutex.withLock {
            messages.forEach { targetAgent.notifyMessage(it) }
        }

        requestingAgent.logger.info { "added \"${targetAgent.name}\" as a participant to thread $id" }
        requestingAgent.session.events.emit(SessionEvent.ThreadParticipantAdded(id, targetAgent.name))
    }

    /**
     * Removes an agent from this thread.
     *
     * @throws SessionException.NotParticipatingException If neither [requestingAgent] nor [targetAgent] is not participating in this thread
     */
    suspend fun removeParticipant(requestingAgent: SessionAgent, targetAgent: SessionAgent) {
        participantsMutex.withLock {
            if (!participants.contains(requestingAgent.name)) {
                requestingAgent.logger.warn { "tried to remove \"${targetAgent.name}\" from thread $id which ${requestingAgent.name} is not a participant of" }
                throw SessionException.NotParticipatingException("Agent ${requestingAgent.name} is not participating in thread ${this.id}.  Agents must be participants of a thread before they can remove others.")
            }

            if (!participants.contains(targetAgent.name)) {
                requestingAgent.logger.warn { "tried to remove non-participating agent \"${targetAgent.name}\" from thread $id" }
                throw SessionException.NotParticipatingException("Agent ${targetAgent.name} is not participating in thread ${this.id}")
            }

            participants.remove(targetAgent.name)
        }

        requestingAgent.logger.info { "removed \"${targetAgent.name}\" as a participant from thread $id" }
        requestingAgent.session.events.emit(SessionEvent.ThreadParticipantRemoved(id, targetAgent.name))
    }

    /**
     * Returns true if the given agent is participating in this thread.  This function is thread-safe.
     */
    suspend fun hasParticipant(agentName: UniqueAgentName): Boolean =
        participantsMutex.withLock { participants.contains(agentName) }

    /**
     * Calls [body] with a list of all messages in this thread.  This function is thread-safe.
     */
    suspend fun withMessageLock(body: suspend (messages: List<SessionThreadMessage>) -> Unit) =
        messagesMutex.withLock {
            body(messages)
        }

    /**
     * Calls [body] with a list of all participants in this thread.  This function is thread-safe.
     */
    suspend fun withParticipantLock(body: suspend (participants: Set<UniqueAgentName>) -> Unit) =
        participantsMutex.withLock {
            body(participants)
        }

    /**
     * Creates a version of this thread that is designed to be placed in an agent's state resource.  This contains all
     * information about the thread, except messages are also filtered with the similar [SessionThreadMessage.asJsonState]
     * function.
     */
    suspend fun asJsonState(): JsonObject = buildJsonObject {
        put("threadId", id)
        put("threadName", name)
        put("owningAgentName", creatorName)

        participantsMutex.withLock {
            put("participatingAgents", JsonArray(participants.map { JsonPrimitive(it) }))
        }

        when (val state = state) {
            is SessionThreadState.Closed -> {
                put("state", "closed")
                put("summary", state.summary)
            }

            SessionThreadState.Open -> {
                put("state", "open")

                messagesMutex.withLock {
                    put("messages", JsonArray(messages.map { it.asJsonState() }))
                }
            }
        }
    }

    /**
     * Transitions this thread to being closed.  All messages in the thread will be deleted, the only remaining data
     * on this thread will be the [summary].
     *
     * @param summary A summary of the thread content previous to its closing.
     *
     * @throws SessionException.ThreadClosedException if the thread is already closed
     */
    suspend fun close(requestingAgent: SessionAgent, summary: String) {
        val state = state
        if (state is SessionThreadState.Closed) {
            requestingAgent.logger.info { "tried to close already closed thread $id, previously closed with summary ${state.summary} (tried closing with new summary \"$summary\")" }
            throw SessionException.ThreadClosedException("Thread ${this.id} cannot be closed because it is not open")
        }

        this.state = SessionThreadState.Closed(summary)
        messagesMutex.withLock { messages.clear() }

        requestingAgent.logger.info { "closed thread $id with summary \"$summary\"" }
        requestingAgent.session.events.emit(SessionEvent.ThreadClosed(id, summary))
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


