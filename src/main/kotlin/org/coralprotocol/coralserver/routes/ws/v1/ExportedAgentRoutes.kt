package org.coralprotocol.coralserver.routes.ws.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager
import org.coralprotocol.coralserver.session.remote.createRemoteSessionServer

private val logger = KotlinLogging.logger {}

/**
 * Websocket between importing server and exporting server
 *
 * Receives messages from importing servers and routes
 */
fun Routing.exportedAgentRoutes(remoteSessionManager: RemoteSessionManager?) {
    webSocket("/ws/v1/exported/{claimId}") {
        if (remoteSessionManager == null) {
            call.respond(HttpStatusCode.InternalServerError, "Remote sessions are disabled")
            return@webSocket
        }

        createRemoteSessionServer(remoteSessionManager)
    }
}