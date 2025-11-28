@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.models.Telemetry
import java.util.UUID

@Serializable
data class SessionThreadMessage(
    val id: String = UUID.randomUUID().toString(),
    val threadId: ThreadId,
    val text: String,
    val senderName: UniqueAgentName,
    val timestamp: Long = System.currentTimeMillis(),
    val mentionNames: Set<UniqueAgentName>,
    var telemetry: Telemetry? = null
)

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
    }

    @Serializable
    @SerialName("thread")
    data class Thread(val threadId: ThreadId) : SessionThreadMessageFilter() {
        override fun matches(message: SessionThreadMessage): Boolean {
            return message.threadId == threadId
        }
    }

    @Serializable
    @SerialName("from")
    data class From(val name: UniqueAgentName) : SessionThreadMessageFilter() {
        override fun matches(message: SessionThreadMessage): Boolean {
            return message.senderName == name
        }
    }
}