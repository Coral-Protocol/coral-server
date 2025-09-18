package org.coralprotocol.coralserver.routes.ws.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.agent.runtime.sendRuntimeEvent
import org.coralprotocol.coralserver.models.SocketEvent
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.models.sendSocketEvent
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.LocalSessionManager

private val logger = KotlinLogging.logger {}

fun Routing.debugWsRoutes(localSessionManager: LocalSessionManager, orchestrator: Orchestrator) {
    webSocket("/ws/v1/debug/{applicationId}/{privacyKey}/{coralSessionId}/") {
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        // TODO (alan): proper appId/privacyKey based lookups when session manager is updated
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")

        val timeout = call.parameters["timeout"]?.toLongOrNull() ?: 1000

        val session = localSessionManager.waitForSession(sessionId, timeout);
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@webSocket
        }

        val debugId = session.registerDebugAgent()
        sendSocketEvent(SocketEvent.DebugAgentRegistered(id = debugId.id))
        sendSocketEvent(SocketEvent.ThreadList(session.getAllThreads().map { it.resolve() }))
        sendSocketEvent(SocketEvent.AgentList(session.getAllAgents(false)))

        session.events.collect { evt ->
            logger.debug { "Received evt: $evt" }
            sendSocketEvent(SocketEvent.Session(evt))
        }
    }

    webSocket("/ws/v1/debug/{applicationId}/{privacyKey}/{coralSessionId}/{agentId}/logs") {
        val applicationId = call.parameters["applicationId"] ?: throw IllegalArgumentException("Missing applicationId")
        val privacyKey = call.parameters["privacyKey"] ?: throw IllegalArgumentException("Missing privacyKey")
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")
        val agentId = call.parameters["agentId"] ?: throw IllegalArgumentException("Missing agentId")

        val bus = orchestrator.getBus(sessionId, agentId) ?: run {
            call.respond(HttpStatusCode.NotFound, "Agent not found")
            return@webSocket;
        };

        bus.events.collect { evt ->
            logger.debug { "Received evt: $evt" }
            sendRuntimeEvent(evt)
        }
    }
}