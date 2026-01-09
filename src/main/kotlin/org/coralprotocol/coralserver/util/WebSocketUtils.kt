@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.util

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.logging.Logger
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

inline fun <reified T> T.toWsFrame(json: Json): Frame.Text =
    Frame.Text(json.encodeToString(this))

inline fun <reified T> Frame.Text.fromWsFrame(json: Json): T =
    json.decodeFromString(this.data.decodeToString())

/**
 * Responds to a GET request with a WebSocket upgrade that pipes down messages from a Flow.  Collection from the flow
 * will be launched in [coroutineScope], the collection job will be canceled if the WebSocket closes - and vice versa
 * if the flow collection is canceled, the WebSocket will be closed.
 */
suspend fun <T> RoutingContext.webSocketFlow(
    name: String,
    logger: Logger,
    coroutineScope: CoroutineScope,
    body: suspend WebSocketSession.() -> Flow<T>
) {
    val trackingId = UUID.randomUUID().toString()
    call.respond(WebSocketUpgrade(call) {
        logger.info { "WS ($name) connection opened: $trackingId" }

        val start = System.currentTimeMillis()
        toServerSession(call).proceedWebSocket {
            val flowJob = body().launchIn(coroutineScope)
            flowJob.invokeOnCompletion {
                launch {
                    close()
                }
            }

            try {
                this@proceedWebSocket.coroutineContext[Job]?.join()
            } finally {
                logger.info { "WS ($name) connection closed: $trackingId (alive for ${(System.currentTimeMillis() - start).milliseconds})" }
                flowJob.cancel()
            }
        }
    })
}