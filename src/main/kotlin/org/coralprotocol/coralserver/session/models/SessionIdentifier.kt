package org.coralprotocol.coralserver.session.models

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class SessionIdentifier(
    @Description("The unique identifier for the session")
    val sessionId: String,

    @Description("The unique identifier for the application")
    val applicationId: String,

    @Description("For future use")
    val privacyKey: String
)