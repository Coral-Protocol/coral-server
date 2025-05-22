package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable
import java.util.*

/**
 * Represents a message in a thread.
 */
@Serializable
data class ResolvedMessage(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mentions: List<String> = emptyList()
)