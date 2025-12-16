@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.SessionId

/**
 * TODO: where should these come from?
 */
@Serializable
@JsonClassDiscriminator("type")
class ServerEvent {
    @Serializable
    @SerialName("session_created")
    data class SessionCreated(val sessionId: SessionId, val namespace: String) : SessionEvent

    @Serializable
    @SerialName("session_closed")
    data class SessionClosed(val sessionId: SessionId, val namespace: String) : SessionEvent

    @Serializable
    @SerialName("namespace_created")
    data class NamespaceCreated(val namespace: String) : SessionEvent

    @Serializable
    @SerialName("session_closed")
    data class NamespaceClosed(val namespace: String) : SessionEvent
}