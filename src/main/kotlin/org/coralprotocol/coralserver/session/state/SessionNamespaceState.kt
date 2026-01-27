package org.coralprotocol.coralserver.session.state

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class SessionNamespaceState(
    @Description("The name of this namespace")
    val name: String,

    @Description("Whether or not this namespace will be deleted when the last session exits")
    val deleteOnLastSessionExit: Boolean,

    @Description("A list of sessions that exist inside this namespace")
    val sessions: List<SessionState>,

    @Description("Annotations for this namespace")
    val annotations: Map<String, String>,
)