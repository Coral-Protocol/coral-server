package org.coralprotocol.coralserver.util

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/*
    These functions are needed from ktor to get their handling of WebSockets (pings, close, etc.), they are normally
    private and locked behind the webSocket Route extension function - which can't be used with type-safe routing nor
    can it easily be hijacked to get auth stuff working the way we want
 */

@OptIn(InternalAPI::class)
suspend fun WebSocketServerSession.proceedWebSocket(handler: suspend DefaultWebSocketServerSession.() -> Unit) {
    val webSockets = application.plugin(WebSockets)

    val session = DefaultWebSocketSession(
        this,
        webSockets.pingIntervalMillis,
        webSockets.timeoutMillis
    ).apply {
        val extensions = call.attributes[WebSockets.EXTENSIONS_KEY]
        start(extensions)
    }

    session.handleServerSession(call, handler)
    session.joinSession()
}

private suspend fun CoroutineScope.joinSession() {
    coroutineContext[Job]!!.join()
}

fun WebSocketSession.toServerSession(call: ApplicationCall): WebSocketServerSession =
    DelegatedWebSocketServerSession(call, this)

fun DefaultWebSocketSession.toServerSession(call: ApplicationCall): DefaultWebSocketServerSession =
    DelegatedDefaultWebSocketServerSession(call, this)

private class DelegatedWebSocketServerSession(
    override val call: ApplicationCall,
    val delegate: WebSocketSession
) : WebSocketServerSession, WebSocketSession by delegate

private class DelegatedDefaultWebSocketServerSession(
    override val call: ApplicationCall,
    val delegate: DefaultWebSocketSession
) : DefaultWebSocketServerSession, DefaultWebSocketSession by delegate


private suspend fun DefaultWebSocketSession.handleServerSession(
    call: ApplicationCall,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    try {
        val serverSession = toServerSession(call)
        handler(serverSession)
        close()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: ChannelIOException) {
        // don't log I/O exceptions
        throw io
    } catch (cause: Throwable) {
        call.application.log.error("Websocket handler failed", cause)
        throw cause
    }
}
