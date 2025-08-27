package org.coralprotocol.coralserver.routes.ws.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import org.coralprotocol.coralserver.models.SocketEvent
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.ExportManager
import org.coralprotocol.coralserver.session.SessionManager
import kotlin.coroutines.suspendCoroutine

private val logger = KotlinLogging.logger {}

fun Routing.exportedAgentRoutes(manager: ExportManager) {
    webSocket("/ws/v1/exported/{id}") {
        val exportedId = call.parameters["id"]

        // val agent = manager.get(exportedId);

        val incomingJob = incoming.receiveAsFlow().onEach { frame ->
            frame as? Frame.Text ?: return@onEach
            val text = frame.readText()
            logger.info { "Received text: $text" }
        }.launchIn(CoroutineScope(Dispatchers.IO))
        // forward to running agent


        // forward agent messages to ws

        incomingJob.join()
    }
}