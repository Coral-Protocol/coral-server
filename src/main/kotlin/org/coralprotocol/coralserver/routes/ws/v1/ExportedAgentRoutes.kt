package org.coralprotocol.coralserver.routes.ws.v1

import io.github.oshai.kotlinlogging.KotlinLogging
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
fun Routing.exportedAgentRoutes(remoteSessionManager: RemoteSessionManager) {
    webSocket("/ws/v1/exported/{claimId}") {
        createRemoteSessionServer(remoteSessionManager)
    }
}