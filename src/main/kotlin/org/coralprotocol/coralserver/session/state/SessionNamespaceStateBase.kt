@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session.state

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.SessionResource

@Serializable
@Description("The base state of a namespace, without session information")
data class SessionNamespaceStateBase(
    @Description("The name of this namespace")
    val name: String,

    @Description("Whether or not this namespace will be deleted when the last session exits")
    val deleteOnLastSessionExit: Boolean,

    override val annotations: Map<String, String>,
) : SessionResource

@Serializable
@Description("The extended state of a namespace, including all sessions")
data class SessionNamespaceStateExtended(
    @Description("Base namespace state")
    val base: SessionNamespaceStateBase,

    @Description("A list of sessions that exist inside this namespace")
    val sessions: List<SessionStateBase>
)

@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionNamespaceState {
    @Serializable
    @SerialName("base")
    data class Base(val state: SessionNamespaceStateBase) : SessionNamespaceState

    @Serializable
    @SerialName("extended")
    data class Extended(val state: SessionNamespaceStateExtended) : SessionNamespaceState
}