package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable

/**
 * Data class for session creation request.
 */
@Serializable
data class CreateSessionRequest(
    val applicationId: String,
    val privacyKey: String
)

/**
 * Data class for session creation response.
 */
@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    val applicationId: String,
    val privacyKey: String
)