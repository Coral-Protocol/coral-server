@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.SessionId

@Serializable
@JsonClassDiscriminator("type")
sealed interface LocalSessionManagerEvent {
    val namespace: String

    @Serializable
    @SerialName("session_created")
    data class SessionCreated(val sessionId: SessionId, override val namespace: String) : LocalSessionManagerEvent

    @Serializable
    @SerialName("session_closing")
    data class SessionClosing(val sessionId: SessionId, override val namespace: String) : LocalSessionManagerEvent

    @Serializable
    @SerialName("session_closed")
    data class SessionClosed(val sessionId: SessionId, override val namespace: String) : LocalSessionManagerEvent

    @Serializable
    @SerialName("namespace_created")
    data class NamespaceCreated(override val namespace: String) : LocalSessionManagerEvent

    @Serializable
    @SerialName("namespace_closed")
    data class NamespaceClosed(override val namespace: String) : LocalSessionManagerEvent
}
