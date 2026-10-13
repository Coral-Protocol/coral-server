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
    val content: List<SessionThreadMessagePart>,
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
     * Only the text parts of [content] are summarized here to keep it as a lightweight state representation
     */
    fun asJsonState() = buildJsonObject {
        put("messageText", content.filterIsInstance<SessionThreadMessagePart.Text>().joinToString("\n") { it.text })
        put("sendingAgentName", senderName)
        put("messageTimestamp", timestamp.toString())

        if (mentionNames.isNotEmpty())
            put("mentionAgentNames", JsonArray(mentionNames.map { JsonPrimitive(it) }))
    }
}

/**
 * The content parts that make up one [SessionThreadMessage] as per ACP/MCP and A2A message structure.
 */
@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionThreadMessagePart {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : SessionThreadMessagePart

    @Serializable
    @SerialName("image")
    data class Image(val data: String, val mimeType: String) : SessionThreadMessagePart

    @Serializable
    @SerialName("audio")
    data class Audio(val data: String, val mimeType: String) : SessionThreadMessagePart

    @Serializable
    @SerialName("embedded_resource")
    data class EmbeddedResource(val resource: ResourceContents) : SessionThreadMessagePart

    @Serializable
    @SerialName("resource_link")
    data class ResourceLink(
        val uri: String,
        val name: String,
        val mimeType: String? = null,
        val title: String? = null,
        val description: String? = null,
    ) : SessionThreadMessagePart
}

/**
 * Content of an embedded resource: inline text or base64 binary, each with a [uri] and optional [mimeType].
 */
@Serializable
@JsonClassDiscriminator("type")
sealed interface ResourceContents {
    val uri: String
    val mimeType: String?

    @Serializable
    @SerialName("text")
    data class Text(val text: String, override val uri: String, override val mimeType: String? = null) :
        ResourceContents

    @Serializable
    @SerialName("blob")
    data class Blob(val blob: String, override val uri: String, override val mimeType: String? = null) :
        ResourceContents
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
