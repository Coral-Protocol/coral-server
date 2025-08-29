package org.coralprotocol.coralserver.server

import io.ktor.serialization.suitableCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.converter
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.charsets.Charset
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

data class ExportAgent(
    var transport: SseServerTransport? = null,
    var wsDeferred: CompletableDeferred<WebSocketServerSession> = CompletableDeferred(),
)

class ExportManager {

    var agents: MutableMap<String, ExportAgent> = mutableMapOf()

    /**
     *
     */
    suspend fun connectAgentTransport(id: String, charset: Charset, transport: SseServerTransport) {
        // TODO: we actually shouldnt getOrPut here, the putting should be done when a remote agent is orchestrated
        val agent = agents.getOrPut(id) { ExportAgent() }
        agent.transport = transport

        transport.onMessage { message ->
            val ws = agent.wsDeferred.await()
            ws.sendSerialized<JSONRPCMessage>(message)
        }

        transport.start()

        val ws = agent.wsDeferred.await()

        if (ws.converter == null) throw IllegalStateException("No converter for websocket!")
        ws.incoming.receiveAsFlow()
            .mapNotNull { ws.converter?.deserialize(charset, typeInfo<JSONRPCMessage>(), it) as JSONRPCMessage? }
            .onEach { message ->
                transport.send(message)
            }.launchIn(CoroutineScope(Dispatchers.IO))

    }

    fun connectAgentWS(id: String, ws: WebSocketServerSession) {
        agents.getOrPut(id) { ExportAgent() }.wsDeferred.complete(ws)
    }

    suspend fun handlePostMessage(agentId: String, call: ApplicationCall) {
        val agent = agents[agentId] ?: return
    }
}
