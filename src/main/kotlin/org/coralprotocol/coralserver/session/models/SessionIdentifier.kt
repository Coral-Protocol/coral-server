package org.coralprotocol.coralserver.session.models

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class SessionIdentifier(
    @Description("The namespace that this session belongs in")
    val namespace: String,

    @Description("The unique identifier for the session")
    val sessionId: String,
)