@file:OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)

package org.coralprotocol.coralserver.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.SessionId
import org.coralprotocol.coralserver.util.InstantSerializer
import org.coralprotocol.coralserver.util.utcTimeNow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@JsonClassDiscriminator("type")
sealed class LocalSessionManagerEvent {
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = utcTimeNow()

    abstract val namespace: String

    @Serializable
    @SerialName("session_created")
    data class SessionCreated(val sessionId: SessionId, override val namespace: String) : LocalSessionManagerEvent()

    @Serializable
    @SerialName("session_closing")
    data class SessionClosing(val sessionId: SessionId, override val namespace: String) : LocalSessionManagerEvent()

    @Serializable
    @SerialName("session_closed")
    data class SessionClosed(val sessionId: SessionId, override val namespace: String) : LocalSessionManagerEvent()

    @Serializable
    @SerialName("namespace_created")
    data class NamespaceCreated(override val namespace: String) : LocalSessionManagerEvent()

    @Serializable
    @SerialName("namespace_closed")
    data class NamespaceClosed(override val namespace: String) : LocalSessionManagerEvent()
}
