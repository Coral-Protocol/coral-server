package org.coralprotocol.coralserver.session.remote

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.websocket.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json

private val clientLogger = KotlinLogging.logger(name = "RemoteSessionConnection.Client")
private val serverLogger = KotlinLogging.logger(name = "RemoteSessionConnection.Server")

private val json = Json {
    encodeDefaults = false
    prettyPrint = false
}

suspend fun ClientWebSocketSession.createRemoteSessionClient() {
    incoming.receiveAsFlow()
        .filterIsInstance<Frame.Text>()
        .collect { frame ->
            val decoded = frame.toSessionFrame()
            clientLogger.info { "Frame: $decoded" }
        }
}

suspend fun WebSocketServerSession.createRemoteSessionServer(remoteSessionManager: RemoteSessionManager) {
    val claimId = call.parameters["claimId"]!!

    // executeClaim will orchestrate the requested agent...
    val remoteSession = remoteSessionManager.executeClaim(claimId)

    // ... when the requested agent launches, the SSE end point will be hit and complete this CompletableDeferred
    val sseTransport = remoteSession.deferredMcpTransport.await()

    // Agent tool calls should be forwarded to the server that imported the agent...
    sseTransport.onMessage {
        outgoing.send(RemoteSessionFrame.Sse(it).toWsFrame())
    }

    // ... and the responses to those tool calls from the importing server should be sent back to the agent
    val webSocketJob = launch {
        incoming.receiveAsFlow()
            .filterIsInstance<Frame.Text>()
            .collect { frame ->
                serverLogger.warn { "Frame: $frame" }

                when (val decoded = frame.toSessionFrame()) {
                    is RemoteSessionFrame.Sse -> {
                        sseTransport.send(decoded.message)
                    }
                }
            }
    }

    // The WebSocket and SSE transport rely on each other and should die if one another dies 💔
    select {
        webSocketJob.onJoin {
            serverLogger.warn { "WebSocket $remoteSession exited" }
        }
        launch {
            sseTransport.start()
        }.onJoin {
            serverLogger.warn { "SSE Transport $remoteSession exited" }
        }
    }

    remoteSession.close(true)
}

private fun Frame.Text.toSessionFrame(): RemoteSessionFrame =
    json.decodeFromString(this.data.decodeToString())

private fun RemoteSessionFrame.toWsFrame(): Frame.Text =
    Frame.Text(json.encodeToString(this))