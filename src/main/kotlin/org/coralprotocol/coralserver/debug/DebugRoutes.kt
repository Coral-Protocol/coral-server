package org.coralprotocol.coralserver.debug

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.collections.*
import io.ktor.websocket.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.mcptools.CreateThreadInput
import org.coralprotocol.coralserver.mcptools.SendMessageInput
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.ResolvedThread
import org.coralprotocol.coralserver.models.Thread
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.session.AgentName
import org.coralprotocol.coralserver.session.Event
import org.coralprotocol.coralserver.session.EventMessage
import org.coralprotocol.coralserver.session.SessionManager


private val logger = KotlinLogging.logger {}

@Serializable
sealed interface SocketEvent {
    @Serializable
    @SerialName("DebugAgentRegistered")
    data class DebugAgentRegistered(val id: String) : SocketEvent

    @Serializable
    @SerialName("ThreadList")
    data class ThreadList(val threads: List<ResolvedThread>) : SocketEvent

    @Serializable
    @SerialName("AgentList")
    data class AgentList(val agents: List<Agent>) : SocketEvent
}

fun Routing.debugRoutes(sessionManager: SessionManager) {
    webSocket("/debug/{applicationId}/{privacyKey}/{coralSessionId}/") {
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")

        val timeout = call.parameters["timeout"]?.toLongOrNull() ?: 1000

        send("waiting")
        val session = sessionManager.waitForSession(sessionId, timeout);
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@webSocket
        }
        send("found")
        val debugId = session.registerDebugAgent()
        sendSerialized<SocketEvent>(SocketEvent.DebugAgentRegistered(id = debugId.id))

        session.events.collect { evt ->
            logger.debug { "Received evt: $evt" }
            sendSerialized(evt)
        }
    }

    post("/debug/{applicationId}/{privacyKey}/{coralSessionId}/{debugAgentId}/thread/") {
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")
        val debugAgentId = call.parameters["debugAgentId"] ?: throw IllegalArgumentException("Missing debugAgentId")

        val session = sessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }

        try {
            val request = call.receive<CreateThreadInput>()
            val thread = session.createThread(
                name = request.threadName,
                creatorId = debugAgentId,
                participantIds = request.participantIds
            )

            call.respond(thread.resolve())
        } catch (e: Exception) {
            logger.error(e) { "Error while creating thread" }
            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
        }
    }
    post("/debug/{applicationId}/{privacyKey}/{coralSessionId}/{debugAgentId}/thread/sendMessage/") {
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"] ?: throw IllegalArgumentException("Missing sessionId")
        val debugAgentId = call.parameters["debugAgentId"] ?: throw IllegalArgumentException("Missing debugAgentId")

        val session = sessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }

        try {
            val request = call.receive<SendMessageInput>()
            val message = session.sendMessage(
                threadId = request.threadId,
                senderId = debugAgentId,
                content = request.content,
                mentions = request.mentions
            )

            call.respond(message.resolve())
        } catch (e: Exception) {
            logger.error(e) { "Error while sending message" }
            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
        }
    }

}