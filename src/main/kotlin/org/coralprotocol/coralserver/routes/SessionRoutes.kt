package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.models.CreateSessionRequest
import org.coralprotocol.coralserver.models.CreateSessionResponse
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures session-related routes.
 */
fun Routing.sessionRoutes() {
    // Session creation endpoint

    post("/sessions") {
        try {
            val request = call.receive<CreateSessionRequest>()

            // Validate application and privacy key
            if (!AppConfigLoader.isValidApplication(request.applicationId, request.privacyKey)) {
                call.respond(HttpStatusCode.BadRequest, "Invalid application ID or privacy key")
                return@post
            }

            // Create a new session
            val session = SessionManager.createSession(request.applicationId, request.privacyKey)

            // Return the session details
            call.respond(
                CreateSessionResponse(
                    sessionId = session.id,
                    applicationId = session.applicationId,
                    privacyKey = session.privacyKey
                )
            )

            logger.info { "Created new session ${session.id} for application ${session.applicationId}" }
        } catch (e: Exception) {
            logger.error(e) { "Error creating session" }
            call.respond(HttpStatusCode.InternalServerError, "Error creating session: ${e.message}")
        }
    }
}