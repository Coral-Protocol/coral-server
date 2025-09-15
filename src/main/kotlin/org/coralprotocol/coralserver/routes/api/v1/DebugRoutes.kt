package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.mcp.tools.models.CreateThreadInput
import org.coralprotocol.coralserver.mcp.tools.models.SendMessageInput
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/debug/thread/{applicationId}/{privacyKey}/{coralSessionId}/{debugAgentId}")
class DebugCreateThread(
    val applicationId: String,
    val privacyKey: String,
    val coralSessionId: String,
    val debugAgentId: String
)

@Resource("/api/v1/debug/thread/sendMessage/{applicationId}/{privacyKey}/{coralSessionId}/{debugAgentId}")
class DebugSendMessage(
    val applicationId: String,
    val privacyKey: String,
    val coralSessionId: String,
    val debugAgentId: String
)

fun Routing.debugApiRoutes(localSessionManager: LocalSessionManager) {
    post<DebugCreateThread>({
        summary = "Create thread"
        description = "Creates a new thread"
        operationId = "debugCreateThread"
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
            pathParameter<String>("debugAgentId") {
                description = "The debug agent ID"
            }
            body<CreateThreadInput> {
                description = "Thread creation request"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Thread created successfully"
            }
            HttpStatusCode.NotFound to {
                description = "Session not found"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
            HttpStatusCode.InternalServerError to {
                description = "Error creating thread"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) { debugRequest ->
        // TODO (alan): proper appId/privacyKey based lookups when session manager is updated
        val session = localSessionManager.getSession(debugRequest.coralSessionId)
            ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

        try {
            val request = call.receive<CreateThreadInput>()
            val thread = session.createThread(
                name = request.threadName,
                creatorId = debugRequest.debugAgentId,
                participantIds = request.participantIds
            )

            call.respond(thread.resolve())
        } catch (e: Exception) {
            logger.error(e) { "Error while creating thread" }
            call.respond(HttpStatusCode.InternalServerError, "Error: ${e.message}")
        }
    }

    post<DebugSendMessage>({
        summary = "Send message"
        description = "Sends a message in debug mode"
        operationId = "debugSendMessage"
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
            pathParameter<String>("debugAgentId") {
                description = "The debug agent ID"
            }
            body<SendMessageInput> {
                description = "The message to send"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Message sent successfully"
            }
            HttpStatusCode.NotFound to {
                description = "Session not found"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
            HttpStatusCode.InternalServerError to {
                description = "Error sending message"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) { debugRequest ->
        // TODO (alan): proper appId/privacyKey based lookups when session manager is updated
        val session = localSessionManager.getSession(debugRequest.coralSessionId)
            ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

        try {
            val request = call.receive<SendMessageInput>()
            val message = session.sendMessage(
                threadId = request.threadId,
                senderId = debugRequest.debugAgentId,
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