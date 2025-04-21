package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.server.createCoralMcpServer
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures SSE-related routes.
 * 
 * @param servers A concurrent map to store server instances by transport session ID
 */
fun Routing.sseRoutes(servers: ConcurrentMap<String, Server>) {
    // SSE endpoint with application, privacy key, and session parameters
    sse("/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"]

        if (applicationId == null || privacyKey == null || sessionId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
            return@sse
        }

        // Get the session
        val session = SessionManager.getSession(sessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@sse
        }

        // Validate that the application and privacy key match the session
        if (session.applicationId != applicationId || session.privacyKey != privacyKey) {
            call.respond(HttpStatusCode.Forbidden, "Invalid application ID or privacy key for this session")
            return@sse
        }

        // Create the transport and server
        val transport = SseServerTransport("/$applicationId/$privacyKey/$sessionId/message", this)
        val server = createCoralMcpServer()

        // Add the server to the session
        session.servers.add(server)
        servers[transport.sessionId] = server

        // Connect the server to the transport
        server.connect(transport)
    }

    // DevMode endpoint - creates session if it doesn't exist and allows any application ID and privacy key
    sse("/devmode/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        val applicationId = call.parameters["applicationId"]
        val privacyKey = call.parameters["privacyKey"]
        val sessionId = call.parameters["coralSessionId"]
        val waitForAgents = call.request.queryParameters["waitForAgents"]?.toIntOrNull() ?: 0

        if (applicationId == null || privacyKey == null || sessionId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
            return@sse
        }

        // Get or create the session (no validation in dev mode)
        val session = SessionManager.getOrCreateSession(sessionId, applicationId, privacyKey)

        // Set the waitForAgents parameter in the session
        if (waitForAgents > 0) {
            session.waitForAgents = waitForAgents
            logger.info { "DevMode: Setting waitForAgents=$waitForAgents for session $sessionId" }
        }

        // Create the transport and server
        val transport = SseServerTransport("/devmode/$applicationId/$privacyKey/$sessionId/message", this)
        val server = createCoralMcpServer()

        // Add the server to the session
        session.servers.add(server)
        servers[transport.sessionId] = server

        // Connect the server to the transport
        server.connect(transport)

        logger.info { "DevMode: Connected to session $sessionId with application $applicationId (waitForAgents=$waitForAgents)" }
    }
}