package org.coralprotocol.coralserver.debug

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.collections.*
import io.ktor.websocket.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import org.coralprotocol.coralserver.session.SessionManager


private val logger = KotlinLogging.logger {}

/**
 * Configures SSE-related routes that handle initial client connections.
 * These endpoints establish bidirectional communication channels and must be hit
 * before any message processing can begin.
 */
fun Routing.debugRoutes(sessionManager: SessionManager) {
    webSocket("/debug/{applicationId}/{privacyKey}/{coralSessionId}/") {
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")

        val timeout = call.parameters["timeout"]?.toLongOrNull() ?: 1000

        send("waiting")
        val session = sessionManager.waitForSession(sessionId, timeout);
        if (session == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Session not found"))
            return@webSocket
        }
        send("found")

        session.events.collect { evt ->
            logger.debug { "Received evt: $evt" }
            sendSerialized(evt)
        }
    }

}