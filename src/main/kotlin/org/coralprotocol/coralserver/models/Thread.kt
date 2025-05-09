package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable
import java.util.*

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