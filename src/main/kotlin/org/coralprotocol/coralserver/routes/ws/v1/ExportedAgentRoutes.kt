package org.coralprotocol.coralserver.routes.ws.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.coralprotocol.coralserver.server.ExportManager

private val logger = KotlinLogging.logger {}

sealed class ExportedAgentFrame {
//    data class
}

/**
 * Websocket between importing server and exporting server
 *
 * Receives messages from importing servers and routes
 */
fun Routing.exportedAgentRoutes(exportManager: ExportManager) {
    webSocket("/ws/v1/exported/{id}") {
        val claimId = call.parameters["id"]!!

        // launch agents
        // wait for sse
        //
        val execution = exportManager.executeClaim(claimId)
        val transports = execution.transport.values.awaitAll()

//        coroutineScope {
//            transports.forEach {
//                it.onMessage {
//                    outgoing.send(it)
//                }
//
//                launch {
//                    it.start()
//                }
//            }
//
//            incoming.consumeAsFlow().collect { frame ->
//                // mcp events (tool call results)
//            }
//        }


        // delay..

        // sse transports !

        // sse -> ws

        // terminal

        // val agent = manager.get(exportedId);
//
//        val incomingJob = incoming.receiveAsFlow().onEach { frame ->
//            frame as? Frame.Text ?: return@onEach
//            val text = frame.readText()
//            logger.info { "Received text: $text" }
//            // messages from local server's agents to remote server's mcp
//
//        }.launchIn(CoroutineScope(Dispatchers.IO))
//        // forward to running agent
//
//        outgoing.send(Frame.Text("Hello World"))
//
//
//        // forward agent messages to ws
//
//        incomingJob.join()
    }
}