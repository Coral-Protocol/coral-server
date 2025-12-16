@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.SessionId

@Serializable
@JsonClassDiscriminator("type")
sealed interface ServerEvent {
    @Serializable
    @SerialName("session_created")
    data class SessionCreated(val sessionId: SessionId, val namespace: String) : ServerEvent

    @Serializable
    @SerialName("session_closed")
    data class SessionClosed(val sessionId: SessionId, val namespace: String) : ServerEvent

    @Serializable
    @SerialName("namespace_created")
    data class NamespaceCreated(val namespace: String) : ServerEvent

    @Serializable
    @SerialName("namespace_closed")
    data class NamespaceClosed(val namespace: String) : ServerEvent
}
