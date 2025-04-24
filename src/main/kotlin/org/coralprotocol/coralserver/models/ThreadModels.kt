package org.coralprotocol.coralserver.models

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * Represents an agent in the system.
 */
@Serializable
data class Agent(
    val id: String,
    val name: String,
    val description: String = "" // Description of the agent's responsibilities
)

/**
 * Represents a message in a thread.
 */
@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mentions: List<String> = emptyList()
)

/**
 * Represents a thread with participants.
 */
@Serializable
data class Thread(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val creatorId: String,
    val participants: MutableList<String> = mutableListOf(),
    val messages: MutableList<Message> = mutableListOf(),
    var isClosed: Boolean = false,
    var summary: String? = null
)
