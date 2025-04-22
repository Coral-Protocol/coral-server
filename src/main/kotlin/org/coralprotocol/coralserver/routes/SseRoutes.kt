package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures SSE-related routes that handle initial client connections.
 * These endpoints establish bidirectional communication channels and must be hit
 * before any message processing can begin.
 */
fun Routing.sseRoutes(servers: ConcurrentMap<String, Server>) {
    sse("/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        handleSseConnection(
            call.parameters,
            this,
            servers,
            isDevMode = false
        )
    }

    sse("/devmode/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        handleSseConnection(
            call.parameters,
            this,
            servers,
            isDevMode = true
        )
    }
}

/**
 * Centralizes SSE connection handling for both production and development modes.
 * Dev mode skips validation and allows on-demand session creation for testing,
 * while production enforces security checks and requires pre-created sessions.
 */
private suspend fun handleSseConnection(
    parameters: Parameters,
    sseProducer: ServerSSESession,
    servers: ConcurrentMap<String, Server>,
    isDevMode: Boolean
): Boolean {
    val applicationId = parameters["applicationId"]
    val privacyKey = parameters["privacyKey"]
    val sessionId = parameters["coralSessionId"]
    val agentId = parameters["agentId"]
    if (agentId == null) {
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Missing agentId parameter")
        return false
    }

    if (applicationId == null || privacyKey == null || sessionId == null) {
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
        return false
    }

    val session = if (isDevMode) {
        val waitForAgents = sseProducer.call.request.queryParameters["waitForAgents"]?.toIntOrNull() ?: 0
        val createdSession = SessionManager.getOrCreateSession(sessionId, applicationId, privacyKey)

        if (waitForAgents > 0) {
            createdSession.devRequiredAgentStartCount = waitForAgents
            logger.info { "DevMode: Setting waitForAgents=$waitForAgents for session $sessionId" }
        }

        createdSession
    } else {
        val existingSession = SessionManager.getSession(sessionId)
        if (existingSession == null) {
            sseProducer.call.respond(HttpStatusCode.NotFound, "Session not found")
            return false
        }

        if (existingSession.applicationId != applicationId || existingSession.privacyKey != privacyKey) {
            sseProducer.call.respond(HttpStatusCode.Forbidden, "Invalid application ID or privacy key for this session")
            return false
        }

        existingSession
    }

    val routePrefix = if (isDevMode) "/devmode" else ""
    val transport = SseServerTransport("$routePrefix/$applicationId/$privacyKey/$sessionId/message", sseProducer)

    val individualServer = CoralAgentIndividualMcp(transport,session, agentId)
    session.coralAgentConnections.add(individualServer)

    val transportSessionId = transport.sessionId
    servers[transportSessionId] = individualServer
    individualServer.connect(transport)

    if (isDevMode) {
        logger.info { "DevMode: Connected to session $sessionId with application $applicationId (waitForAgents=${session.devRequiredAgentStartCount})" }
    }

    return true
}