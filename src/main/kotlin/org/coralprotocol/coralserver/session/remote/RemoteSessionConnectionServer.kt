package org.coralprotocol.coralserver.session.remote

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.websocket.*
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

private val logger = KotlinLogging.logger { }

class RemoteSessionConnectionServer(
    val session: WebSocketSession,
    val transport: SseServerTransport
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun start() {
        val webSocketJob = scope.launch {
            logger.debug { "Starting WebSocket reader" }

            for (frame in session.incoming) {
                if (frame !is Frame.Text)
                    continue

                when (val rsf = frame.toSessionFrame()) {
                    is RemoteSessionFrame.Sse -> transport.send(rsf.message)
                }
            }
        }

        transport.onMessage { message ->
            session.outgoing.send(message.toWsFrame())
        }

        val sseTransportJob = scope.launch {
            logger.debug { "Starting SseTransport reader" }
            transport.start()
        }

        select {
            webSocketJob.onJoin {
                logger.warn { "WebSocket exited" }
            }

            sseTransportJob.onJoin {
                logger.warn { "SseTransport exited" }
            }
        }
    }
}