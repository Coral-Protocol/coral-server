package org.coralprotocol.coralserver.routes.sse.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.contentnegotiation.suitableCharset
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.request.uri
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.server.ExportManager

private val logger = KotlinLogging.logger {}

/**
 * Configures SSE-related routes that handle initial client connections.
 * These endpoints establish bidirectional communication channels and must be hit
 * before any message processing can begin.
 */
fun Routing.exportedAgentSseRoutes(servers: ConcurrentMap<String, Server>, exportManager: ExportManager) {
    suspend fun ServerSSESession.handleSseConnection(isDevMode: Boolean = false) {
        handleSseConnection(
            call,
            "coral://" + call.request.host() + ":" + call.request.port() + call.request.uri,
            call.parameters,
            this,
            servers,
            exportManager = exportManager,
            isDevMode
        )
    }

    sse("/sse/v1/export/{externalId}") {
        handleSseConnection()
    }
    /*
        The following routes are added as aliases for any piece of existing software that requires that the URL ends
        with /sse
     */
    sse("/sse/v1/export/{externalId}") {
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
    exportManager: ExportManager,
    isDevMode: Boolean
): Boolean {
    val agentId = parameters["externalId"]
    val agentDescription: String = parameters["agentDescription"] ?: agentId ?: "no description"
    val maxWaitForMentionsTimeout = parameters["maxWaitForMentionsTimeout"]?.toLongOrNull() ?: 60000

    if (agentId == null) {
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Missing externalId parameter")
        return false
    }

    val endpoint = "/api/v1/message/export/$agentId"
    val transport = SseServerTransport(endpoint, sseProducer)

    // TODO: better route err handling
    val agent = try {
        exportManager.connectAgentTransport(agentId, charset = call.suitableCharset(), transport)
    } catch (e: Exception) {
        logger.info { "Agent ID $agentId already connected!" }
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Agent ID already connected")
        return false
    }

    return true
}
