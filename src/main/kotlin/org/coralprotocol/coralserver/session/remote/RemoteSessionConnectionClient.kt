package org.coralprotocol.coralserver.session.remote

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.websocket.*
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport

private val logger = KotlinLogging.logger { }

class RemoteSessionConnectionClient(
    val session: WebSocketSession
): AbstractTransport() {
    override suspend fun start() {
        logger.debug { "Starting RemoteSessionClient" }

        for (frame in session.incoming) {
            if (frame !is Frame.Text)
                continue

            when (val rsf = frame.toSessionFrame()) {
                is RemoteSessionFrame.Sse -> _onMessage(rsf.message)
            }
        }
    }

    override suspend fun send(message: JSONRPCMessage) {
        val frame = message.toWsFrame()
        session.outgoing.send(frame)
    }

    override suspend fun close() {
        logger.debug { "Closing RemoteSessionClient" }

        session.close()
    }
}