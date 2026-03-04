@file:OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)

package org.coralprotocol.coralserver.events

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.coralserver.session.state.SessionNamespaceStateBase
import org.coralprotocol.coralserver.session.state.SessionStateBase
import org.coralprotocol.coralserver.util.InstantSerializer
import org.coralprotocol.coralserver.util.utcTimeNow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@JsonClassDiscriminator("type")
sealed class LocalSessionManagerEvent {
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = utcTimeNow()

    @Serializable
    @SerialName("session_created")
    data class SessionCreated(
        val initialSessionState: SessionStateBase,
        val namespaceState: SessionNamespaceStateBase,
    ) : LocalSessionManagerEvent()

    @Serializable
    @SerialName("session_running")
    data class SessionRunning(
        val sessionState: SessionStateBase,
        val namespaceState: SessionNamespaceStateBase,
    ) : LocalSessionManagerEvent()

    @Serializable
    @SerialName("session_closing")
    data class SessionClosing(
        val sessionState: SessionStateBase,
        val namespaceState: SessionNamespaceStateBase,
    ) : LocalSessionManagerEvent()

    @Serializable
    @SerialName("session_closed")
    data class SessionClosed(
        val finalSessionState: SessionStateBase,
        val namespaceState: SessionNamespaceStateBase,
    ) : LocalSessionManagerEvent()

    @Serializable
    @SerialName("namespace_created")
    data class NamespaceCreated(
        val initialState: SessionNamespaceStateBase
    ) : LocalSessionManagerEvent()

    @Serializable
    @SerialName("namespace_closed")
    data class NamespaceClosed(
        val finalState: SessionNamespaceStateBase
    ) : LocalSessionManagerEvent()

    fun hasSessionAnnotation(key: String, value: String): Boolean {
        return try {
            when (this) {
                is SessionClosed -> this.finalSessionState.annotations[key] == value
                is SessionClosing -> this.sessionState.annotations[key] == value
                is SessionCreated -> this.initialSessionState.annotations[key] == value
                is SessionRunning -> this.sessionState.annotations[key] == value

                else -> false
            }
        } catch (_: SessionException.InvalidNamespace) {
            false
        } catch (_: SessionException.InvalidSession) {
            false
        }
    }

    fun hasNamespaceAnnotation(key: String, value: String): Boolean {
        return try {
            when (this) {
                is SessionClosed -> this.namespaceState.annotations[key] == value
                is SessionClosing -> this.namespaceState.annotations[key] == value
                is SessionCreated -> this.namespaceState.annotations[key] == value
                is SessionRunning -> this.namespaceState.annotations[key] == value
                is NamespaceClosed -> this.finalState.annotations[key] == value
                is NamespaceCreated -> this.initialState.annotations[key] == value
            }
        } catch (_: SessionException.InvalidNamespace) {
            false
        } catch (_: SessionException.InvalidSession) {
            false
        }
    }

    fun isInNamespace(namespaceName: String) = when (this) {
        is NamespaceClosed -> this.finalState.name == namespaceName
        is NamespaceCreated -> this.initialState.name == namespaceName
        is SessionClosed -> this.namespaceState.name == namespaceName
        is SessionClosing -> this.namespaceState.name == namespaceName
        is SessionCreated -> this.namespaceState.name == namespaceName
        is SessionRunning -> this.namespaceState.name == namespaceName
    }
}
