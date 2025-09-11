package org.coralprotocol.coralserver.routes.sse.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.session.LocalSessionManager

private val logger = KotlinLogging.logger {}

/**
 * Configures SSE-related routes that handle initial client connections.
 * These endpoints establish bidirectional communication channels and must be hit
 * before any message processing can begin.
 */
fun Routing.connectionSseRoutes(servers: ConcurrentMap<String, Server>, localSessionManager: LocalSessionManager) {
    suspend fun ServerSSESession.handleSseConnection(isDevMode: Boolean = false) {
        handleSseConnection(
            "coral://" + call.request.host() + ":" + call.request.port() + call.request.uri,
            call.parameters,
            this,
            servers,
            localSessionManager = localSessionManager,
            isDevMode
        )
    }

    sse("/sse/v1/{applicationId}/{privacyKey}/{coralSessionId}") {
        handleSseConnection()
    }

    sse("/sse/v1/devmode/{applicationId}/{privacyKey}/{coralSessionId}") {
        handleSseConnection(true)
    }

    /*
        The following routes are added as aliases for any piece of existing software that requires that the URL ends
        with /sse
     */

    sse("/sse/v1/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        handleSseConnection()
    }

    sse("/sse/v1/devmode/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
        handleSseConnection(true)
    }
}

/**
 * Centralizes SSE connection handling for both production and development modes.
 * Dev mode skips validation and allows on-demand session creation for testing,
 * while production enforces security checks and requires pre-created sessions.
 */
private suspend fun handleSseConnection(
    uri: String,
    parameters: Parameters,
    sseProducer: ServerSSESession,
    servers: ConcurrentMap<String, Server>,
    localSessionManager: LocalSessionManager,
    isDevMode: Boolean
): Boolean {
    val applicationId = parameters["applicationId"]
    val privacyKey = parameters["privacyKey"]
    val sessionId = parameters["coralSessionId"]
    val agentId = parameters["agentId"]
    val agentDescription: String = parameters["agentDescription"] ?: agentId ?: "no description"

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
        val createdSession = localSessionManager.getOrCreateSession(sessionId, applicationId, privacyKey, null)

        if (waitForAgents > 0) {
            createdSession.devRequiredAgentStartCount = waitForAgents
            logger.info { "DevMode: Setting waitForAgents=$waitForAgents for session $sessionId" }
        }

        createdSession
    } else {
        val existingSession = localSessionManager.getSession(sessionId)
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
    val currentCount = session.getRegisteredAgentsCount()

    // TODO: better route err handling
    val agent = try {
        val agent = when (isDevMode) {
            true -> {
                val agent = session.registerAgent(agentId, uri, agentDescription, force = true)
                session.connectAgent(agentId)
                agent!! // never null when force = true
            }
            false -> {
                val agent = session.connectAgent(agentId)
                if(agent != null) {
                    agent.description = agentDescription
                    agent.mcpUrl = uri
                }
                agent
            }
        }
        if (agent == null) {
            logger.info { "Agent ID $agentId does not exist!" }
            sseProducer.call.respond(HttpStatusCode.NotFound, "Agent ID does not exist")
            return false
        }
        agent
    } catch (e: Exception) {
        logger.info { "Agent ID $agentId already connected!" }
        sseProducer.call.respond(HttpStatusCode.BadRequest, "Agent ID already connected")
        return false
    }

    logger.info { "DevMode: Current agent count for session ${session.id} (object id: ${session}) (from sessionmanager: ${localSessionManager}): $currentCount, waiting for: ${session.devRequiredAgentStartCount}" }
    val newCount = session.getRegisteredAgentsCount()
    logger.info { "DevMode: New agent count for session ${session.id} (object id: ${session})after registering: $newCount" }

    val routeSuffix = if (isDevMode) "devmode/" else ""
    val endpoint = "/api/v1/message/$routeSuffix$applicationId/$privacyKey/$sessionId"
    val transport = SseServerTransport(endpoint, sseProducer)

    val individualServer = CoralAgentIndividualMcp(
        localSession = session,
        connectedAgentId = agentId,
        extraTools = agent.extraTools,
        plugins = agent.coralPlugins
    )

    val transportSessionId = transport.sessionId
    servers[transportSessionId] = individualServer

    val success = session.waitForGroup(agentId, 60000)
    if (success) {
        logger.info { "Agent $agentId successfully waited for group" }
    } else {
        logger.warn { "Agent $agentId failed waiting for group, proceeding anyway.." }
    }

    if (isDevMode) {
        logger.info { "DevMode: Connected to session $sessionId with application $applicationId (waitForAgents=${session.devRequiredAgentStartCount})" }

        if (session.devRequiredAgentStartCount > 0) {
            if (newCount < session.devRequiredAgentStartCount) {

                val success = session.waitForAgentCount(session.devRequiredAgentStartCount, 60000)
                if (success) {
                    logger.info { "DevMode: Successfully waited for ${session.devRequiredAgentStartCount} agents to connect" }
                } else {
                    logger.warn { "DevMode: Timeout waiting for ${session.devRequiredAgentStartCount} agents to connect, proceeding anyway" }
                }
            } else {
                logger.info { "DevMode: Required agent count already reached" }
            }
        }
    }

    individualServer.connect(transport)
    individualServer.onClose {
        logger.info { "Agent $agentId disconnected via server." }
        session.disconnectAgent(agentId);
    }
    return true
}
