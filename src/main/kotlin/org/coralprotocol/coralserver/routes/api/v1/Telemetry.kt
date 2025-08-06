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
import org.coralprotocol.coralserver.session.SessionManager
import org.coralprotocol.coralserver.models.TelemetryPost as TelemetryPostModel

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/telemetry/{sessionId}/{threadId}/{messageId}")
class TelemetryGet(val sessionId: String, val threadId: String, val messageId: String) {
    fun intoMessage(sessionManager: SessionManager): Message {
        val session = sessionManager.getSession(sessionId) ?: throw RouteException(
            HttpStatusCode.NotFound,
            "Session not found"
        )

        val thread = session.getThread(threadId) ?: throw RouteException(
            HttpStatusCode.NotFound,
            "Thread not found"
        )

        // TODO: messages should be a map (@Caelum told me to do this (the bad code not the comment))
        return thread.messages.find { it.id == messageId } ?: throw RouteException(
            HttpStatusCode.NotFound,
            "Message not found"
        )
    }
}

@Resource("/api/v1/telemetry/{sessionId}")
class TelemetryPost(val sessionId: String)

fun Routing.telemetryRoutes(sessionManager: SessionManager) {
    get<TelemetryGet>({
        summary = "Get telemetry"
        description = "Fetches telemetry information for a given message"
        operationId = "getTelemetry"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<Telemetry> {
                    description = "Telemetry data"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Telemetry data not found for specified message"
            }
        }
    }) { telemetry ->
        call.respond(telemetry.intoMessage(sessionManager).telemetry ?: throw RouteException(
            HttpStatusCode.NotFound,
            "Telemetry not found"
        ))
    }

    post<TelemetryPost>({
        summary = "Add telemetry"
        description = "Attaches telemetry information a list of messages"
        operationId = "addTelemetry"
        request {
            body<TelemetryPostModel> {
                description = "Telemetry data"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
        }
    }) { post ->
        val model = call.receive<TelemetryPostModel>()
        for (target in model.targets) {
            val message = TelemetryGet(post.sessionId, target.threadId, target.messageId)
                .intoMessage(sessionManager)

            message.telemetry = model.data;
        }

        call.respond(status = HttpStatusCode.OK, "")
    }
}
