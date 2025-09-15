package org.coralprotocol.coralserver.routes.sse.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.session.remote.RemoteSessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures SSE-related routes that handle initial client connections.
 * These endpoints establish bidirectional communication channels and must be hit
 * before any message processing can begin.
 */
fun Routing.exportedAgentSseRoutes(
    servers: ConcurrentMap<String, Server>,
    remoteSessionManager: RemoteSessionManager?
) {
    suspend fun ServerSSESession.handleSseConnection(isDevMode: Boolean = false) {
        handleSseConnection(
            call,
            "coral://" + call.request.host() + ":" + call.request.port() + call.request.uri,
            call.parameters,
            this,
            servers,
            remoteSessionManager = remoteSessionManager,
            isDevMode
        )
    }

    sse("/sse/v1/export/{remoteSessionId}") {
        handleSseConnection()
    }
    /*
        The following routes are added as aliases for any piece of existing software that requires that the URL ends
        with /sse
     */
    sse("/sse/v1/export/{remoteSessionId}") {
        handleSseConnection()
    }
}

/**
 * Centralizes SSE connection handling for both production and development modes.
 * Dev mode skips validation and allows on-demand session creation for testing,
 * while production enforces security checks and requires pre-created sessions.
 */
private suspend fun handleSseConnection(
    call: ApplicationCall,
    uri: String,
    parameters: Parameters,
    sseProducer: ServerSSESession,
    servers: ConcurrentMap<String, Server>,
    remoteSessionManager: RemoteSessionManager?,
    isDevMode: Boolean
) {
    // TODO: Address unused variables
    val remoteSessionId = parameters["remoteSessionId"]
//    val agentDescription: String = parameters["agentDescription"] ?: remoteSessionId ?: "no description"
//    val maxWaitForMentionsTimeout = parameters["maxWaitForMentionsTimeout"]?.toLongOrNull() ?: 60000

    if (remoteSessionManager == null) {
        sseProducer.call.respond(HttpStatusCode.InternalServerError, "Remote sessions are disabled")
        return
    }

    if (remoteSessionId == null) {
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Missing remoteSessionId parameter")
        return
    }

    val endpoint = "/api/v1/message/export/$remoteSessionId"
    val transport = SseServerTransport(endpoint, sseProducer)

    val session = remoteSessionManager.findSession(remoteSessionId)
    if (session == null) {
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Remote session not found")
        return
    }

    session.connectMcpTransport(transport)
}
