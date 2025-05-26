package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable
import java.util.*

/**
 * Immutable representation of a thread with participants.
 * All collections are immutable to ensure thread safety.
 */
@Serializable
data class ImmutableThread(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val creatorId: String,
    val participants: Set<String> = emptySet(),
    val messages: List<Message> = emptyList(),
    val isClosed: Boolean = false,
    val summary: String? = null
) {
    /**
     * Add a participant to the thread.
     */
    fun addParticipant(participantId: String): ImmutableThread {
        return copy(participants = participants + participantId)
    }
    
    /**
     * Remove a participant from the thread.
     */
    fun removeParticipant(participantId: String): ImmutableThread {
        return copy(participants = participants - participantId)
    }
    
    /**
     * Add a message to the thread.
     */
    fun addMessage(message: Message): ImmutableThread {
        return copy(messages = messages + message)
    }
    
    /**
     * Close the thread with a summary.
     */
    fun close(summary: String): ImmutableThread {
        return copy(isClosed = true, summary = summary)
    }
}