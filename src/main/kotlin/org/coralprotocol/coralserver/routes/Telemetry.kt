package org.coralprotocol.coralserver.routes

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.Routing
import org.coralprotocol.coralserver.models.Message
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.SessionManager
import org.coralprotocol.coralserver.models.TelemetryPost as TelemetryPostModel
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post

private val logger = KotlinLogging.logger {}

@Resource("/sessions/{sessionId}/telemetry/{threadId}/{messageId}")
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

@Resource("/sessions/{sessionId}/telemetry")
class TelemetryPost(val sessionId: String)

fun Routing.telemetryRoutes(sessionManager: SessionManager) {
    get<TelemetryGet>({
        description = "Test"
    }) { telemetry ->
        call.respond(telemetry.intoMessage(sessionManager).resolve())
    }

    post<TelemetryPost>(
        {
            description = "another test"
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
        }
    ) { post ->
        val model = call.receive<TelemetryPostModel>()
        for (target in model.targets) {
            val message = TelemetryGet(post.sessionId, target.threadId, target.messageId)
                .intoMessage(sessionManager)

            message.telemetry = model.data;
        }

        call.respond(status = HttpStatusCode.OK, "")
    }
}
