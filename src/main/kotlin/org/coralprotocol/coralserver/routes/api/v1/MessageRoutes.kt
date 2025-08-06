package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.Routing
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import org.coralprotocol.coralserver.session.SessionManager
import kotlin.NoSuchElementException
import kotlin.String

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/message/{applicationId}/{privacyKey}/{coralSessionId}")
class Message(val applicationId: String, val privacyKey: String, val coralSessionId: String)

@Resource("/api/v1/message/devmode/{applicationId}/{privacyKey}/{coralSessionId}")
class DevModeMessage(val applicationId: String, val privacyKey: String, val coralSessionId: String)

/**
 * Configures message-related routes.
 * 
 * @param servers A concurrent map to store server instances by transport session ID
 */
fun Routing.messageRoutes(servers: ConcurrentMap<String, Server>, sessionManager: SessionManager) {
    // Message endpoint with application, privacy key, and session parameters
    post<Message>({
        summary = "Send message"
        description = "Sends a message"
        operationId = "sendMessage"
        request {
            pathParameter<String>("applicationId") {
                description = "The application ID"
            }
            pathParameter<String>("privacyKey") {
                description = "The privacy key"
            }
            pathParameter<String>("coralSessionId") {
                description = "The Coral session ID"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
            HttpStatusCode.Forbidden to {
                description = "Invalid application ID or privacy key"
            }
            HttpStatusCode.NotFound to {
                description = "Invalid session ID"
            }
            HttpStatusCode.InternalServerError to {
                description = "MCP error"
            }
        }
    }) { message ->
        logger.debug { "Received Message" }

        val session = sessionManager.getSession(message.coralSessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }

        // Validate that the application and privacy key match the session
        if (session.applicationId != message.applicationId || session.privacyKey != message.privacyKey) {
            call.respond(HttpStatusCode.Forbidden, "Invalid application ID or privacy key for this session")
            return@post
        }

        // Get the transport
        val transport = servers[message.coralSessionId]?.transport as? SseServerTransport
        if (transport == null) {
            call.respond(HttpStatusCode.NotFound, "Transport not found")
            return@post
        }

        // Handle the message
        try {
            transport.handlePostMessage(call)
        } catch (e: NoSuchElementException) {
            logger.error(e) { "This error likely comes from an inspector or non-essential client and can probably be ignored. See https://github.com/modelcontextprotocol/kotlin-sdk/issues/7" }
            call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
        }
    }

    // DevMode message endpoint - no validation
    post<DevModeMessage>({
        summary = "Send development message"
        description = "Sends a dev-mode message"
        operationId = "sendDevMessage"
        request {
            pathParameter<String>("applicationId") {
                description = "The application ID"
            }
            pathParameter<String>("privacyKey") {
                description = "The privacy key"
            }
            pathParameter<String>("coralSessionId") {
                description = "The Coral session ID"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
            HttpStatusCode.NotFound to {
                description = "Invalid session ID"
            }
            HttpStatusCode.InternalServerError to {
                description = "MCP error"
            }
        }
    }) { message ->
        logger.debug { "Received DevMode Message" }

        // Get the session. It should exist even in dev mode as it was created in the sse endpoint
        val session = sessionManager.getSession(message.coralSessionId)
        if (session == null) {
            call.respond(HttpStatusCode.NotFound, "Session not found")
            return@post
        }

        // Get the transport
        val transport = servers[message.coralSessionId]?.transport as? SseServerTransport
        if (transport == null) {
            call.respond(HttpStatusCode.NotFound, "Transport not found")
            return@post
        }

        // Handle the message
        try {
            transport.handlePostMessage(call)
        } catch (e: NoSuchElementException) {
            logger.error(e) { "This error likely comes from an inspector or non-essential client and can probably be ignored. See https://github.com/modelcontextprotocol/kotlin-sdk/issues/7" }
            call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
        }
    }
}