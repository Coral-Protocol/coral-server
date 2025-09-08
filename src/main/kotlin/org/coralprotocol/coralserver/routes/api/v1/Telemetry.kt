package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.models.Message
import org.coralprotocol.coralserver.models.Telemetry
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.models.TelemetryPost as TelemetryPostModel

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/telemetry/{sessionId}/{threadId}/{messageId}")
class TelemetryGet(val sessionId: String, val threadId: String, val messageId: String) {
    fun intoMessage(localSessionManager: LocalSessionManager): Message {
        val session = localSessionManager.getSession(sessionId) ?: throw RouteException(
            HttpStatusCode.NotFound,
            "Session not found"
        )

        val thread = session.getThread(threadId) ?: throw RouteException(
            HttpStatusCode.NotFound,
            "Thread not found"
        )

        // TODO: messages should be a map (@Caelum told me to do this (the bad code not the comment))
        // (Caelum: for the record, I told @Seafra to make it a map, that is the part he is referring to as bad code...)
        return thread.messages.find { it.id == messageId } ?: throw RouteException(
            HttpStatusCode.NotFound,
            "Message not found"
        )
    }
}

@Resource("/api/v1/telemetry/{sessionId}")
class TelemetryPost(val sessionId: String)

fun Routing.telemetryApiRoutes(localSessionManager: LocalSessionManager) {
    get<TelemetryGet>({
        summary = "Get telemetry"
        description = "Fetches telemetry information for a given message"
        operationId = "getTelemetry"
        request {
            pathParameter<String>("sessionId") {
                description = "The session ID"
            }
            pathParameter<String>("threadId") {
                description = "The thread ID"
            }
            pathParameter<String>("messageId") {
                description = "The message ID"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<Telemetry> {
                    description = "Telemetry data"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Telemetry data not found for specified message"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) { telemetry ->
        call.respond(telemetry.intoMessage(localSessionManager).telemetry ?: throw RouteException(
            HttpStatusCode.NotFound,
            "Telemetry not found"
        ))
    }

    post<TelemetryPost>({
        summary = "Add telemetry"
        description = "Attaches telemetry information a list of messages"
        operationId = "addTelemetry"
        request {
            pathParameter<String>("sessionId") {
                description = "The session ID"
            }
            body<TelemetryPostModel> {
                description = "Telemetry data"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
            HttpStatusCode.NotFound to {
                description = "Specified messages were not found"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) { post ->
        val model = call.receive<TelemetryPostModel>()
        for (target in model.targets) {
            val message = TelemetryGet(post.sessionId, target.threadId, target.messageId)
                .intoMessage(localSessionManager)

            // maybe error if there is telemetry on this message already?
            message.telemetry = model.data
            logger.info { "Adding telemetry to ${target.threadId}/${message.id} in session \"${post.sessionId}\"" }
        }

        call.respond(status = HttpStatusCode.OK, "")
    }
}
