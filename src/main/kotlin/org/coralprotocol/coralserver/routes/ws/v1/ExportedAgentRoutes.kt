package org.coralprotocol.coralserver.routes.ws.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import org.coralprotocol.coralserver.models.SocketEvent
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

fun Routing.exportedAgentRoutes() {
    webSocket("/ws/v1/exported/{id}") {
        val exportedId = call.parameters["id"]

        // val agent = manager.get(exportedId);

        incoming.receiveAsFlow().onEach { frame ->
            logger.trace { "received $frame" }
            // forward to running agent
        }

        // forward agent messages to ws

    }
}