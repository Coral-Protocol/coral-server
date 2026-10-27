@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.util.InstantSerializer
import org.coralprotocol.coralserver.util.utcTimeNow
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

typealias MessageId = String

@OptIn(ExperimentalTime::class)
@Serializable
data class SessionThreadMessage(
    val id: MessageId = UUID.randomUUID().toString(),
    val threadId: ThreadId,
    val text: String,
    val senderName: UniqueAgentName,
    val mentionNames: Set<UniqueAgentName>,

    @Serializable(with = InstantSerializer::class)
    @Suppress("unused")
    val timestamp: Instant = utcTimeNow(),
) {
    /**
     * Creates a version of this message that is designed to be placed in an agent's state resource.  This contains only
     * vital information about the message and does not include any IDs.
     *
     * @param viewer The agent whose state this is for.  If given, and if the message was not sent by [viewer], the
     * message is marked with whether it was returned to [viewer] by a waiting tool.
     */
    fun asJsonState(viewer: SessionAgent? = null) = buildJsonObject {
        put("messageText", text)
        put("sendingAgentName", senderName)
        put("messageTimestamp", timestamp.toString())

        if (mentionNames.isNotEmpty())
            put("mentionAgentNames", JsonArray(mentionNames.map { JsonPrimitive(it) }))

        if (viewer != null && viewer.name != senderName)
            put("receivedFromWaitingTool", viewer.hasReceived(this@SessionThreadMessage))
    }
}

@Serializable
@JsonClassDiscriminator("type")
sealed class SessionThreadMessageFilter {
    abstract fun matches(message: SessionThreadMessage): Boolean

    @Serializable
    @SerialName("mentions")
    data class Mentions(val name: UniqueAgentName) : SessionThreadMessageFilter() {
        override fun matches(message: SessionThreadMessage): Boolean {
            return message.mentionNames.contains(name)
        }

        override fun toString(): String {
            return "mentions: $name"
        }
    }

    @Serializable
    @SerialName("thread")
    data class Thread(val threadId: ThreadId) : SessionThreadMessageFilter() {
        override fun matches(message: SessionThreadMessage): Boolean {
            return message.threadId == threadId
        }

        override fun toString(): String {
            return "in_thread: $threadId"
        }
    }

    @Serializable
    @SerialName("from")
    data class From(val name: UniqueAgentName) : SessionThreadMessageFilter() {
        override fun matches(message: SessionThreadMessage): Boolean {
            return message.senderName == name
        }

        override fun toString(): String {
            return "from: $name"
        }
    }
}
