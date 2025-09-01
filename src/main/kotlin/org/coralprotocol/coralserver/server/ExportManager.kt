package org.coralprotocol.coralserver.server

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import java.util.*

data class ExportAgent(
    var transport: SseServerTransport? = null,
    var wsDeferred: CompletableDeferred<WebSocketServerSession> = CompletableDeferred(),
)

enum class ClaimState {
    PENDING,
    ACTIVE,
}

data class ExportedAgentClaim(
    val id: String = UUID.randomUUID().toString(),
    val agents: List<GraphAgent>,
    var state: ClaimState = ClaimState.PENDING,
)

/**
 * Virtual session that represents a reference to an importing server's Session
 */
data class ExportedSession(
    val id: String,
    // [agent name] = transport
    val transport: Map<String, CompletableDeferred<SseServerTransport>>,
)

class ExportManager(
    val orchestrator: Orchestrator
){
    private val scope = CoroutineScope(Dispatchers.IO)

    var agents: MutableMap<String, ExportAgent> = mutableMapOf()
    val claims: MutableMap<String, ExportedAgentClaim> = mutableMapOf()

    /**
     * Registers a new claim for agents
     */
    fun createClaim(agents: List<GraphAgent>): String {
        val claim = ExportedAgentClaim(agents = agents)
        claims[claim.id] = claim

        return claim.id
    }

    /**
     *
     */
    fun executeClaim(id: String): ExportedSession {
        val claim = claims[id] ?: throw IllegalArgumentException("Bad claim ID")

        if (claim.state != ClaimState.PENDING)
            throw IllegalStateException("Claim has already been executed")

        claim.state = ClaimState.ACTIVE

        // todo: orchestrate agents, return handle
        //orchestrator.spawn()

        return ExportedSession(id, emptyMap())
    }
    /**
     *
     */
//    suspend fun connectAgentTransport(id: String, charset: Charset, transport: SseServerTransport) {
//        // TODO: we actually shouldnt getOrPut here, the putting should be done when a remote agent is orchestrated
//        val agent = agents.getOrPut(id) { ExportAgent() }
//        agent.transport = transport
//
//        scope.launch {
//            transport.onMessage { message ->
//                val ws = agent.wsDeferred.await()
//                ws.sendSerialized<JSONRPCMessage>(message)
//            }
//
//            val ws = agent.wsDeferred.await()
//            if (ws.converter == null) throw IllegalStateException("No converter for websocket!")
//            ws.incoming.receiveAsFlow()
//                .mapNotNull { ws.converter?.deserialize(charset, typeInfo<JSONRPCMessage>(), it) as JSONRPCMessage? }
//                .onEach { message ->
//                    transport.send(message)
//                }.launchIn(CoroutineScope(Dispatchers.I
//        }
//
//        // returns when sse dies
//        transport.start()
//    }

    fun connectAgentWS(id: String, ws: WebSocketServerSession) {
        agents.getOrPut(id) { ExportAgent() }.wsDeferred.complete(ws)
    }

    suspend fun handlePostMessage(agentId: String, call: ApplicationCall) {
        val agent = agents[agentId] ?: return
    }

    fun registerSseTransport() {
        // read sse on threads
    }
}
