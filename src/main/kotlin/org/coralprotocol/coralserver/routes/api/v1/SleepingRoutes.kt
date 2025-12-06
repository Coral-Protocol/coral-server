package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.github.smiley4.ktoropenapi.resources.put
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager

@Resource("/api/v1/sessions/{sessionId}/{agentId}/sleeping")
class Sleeping(val sessionId: String, val agentId: String)

@Serializable
data class SleepState(val sleeping: Boolean)


fun Routing.sleepingRoutes(localSessionManager: LocalSessionManager) {

    get<Sleeping>({
        summary = "Get agent sleeping state"
        description = "Returns whether a given agent instance in a session is sleeping"
        operationId = "getAgentSleeping"
        request {
            pathParameter<String>("sessionId") { description = "The session ID" }
            pathParameter<String>("agentId") { description = "The agent ID within the session" }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<SleepState> { description = "Current sleeping state" }
            }
            HttpStatusCode.NotFound to {
                description = "Session or agent not found"
                body<RouteException> { description = "Error details" }
            }
        }
    }) {
        val session = localSessionManager.getSession(it.sessionId) ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")
        val agent = session.getAgent(it.agentId) ?: throw RouteException(HttpStatusCode.NotFound, "Agent not found in session")
        call.respond(HttpStatusCode.OK, SleepState(agent.sleeping))
    }

    put<Sleeping>({
        summary = "Set agent sleeping state"
        description = "Sets whether a given agent instance in a session is sleeping"
        operationId = "setAgentSleeping"
        request {
            body<SleepState> { description = "Desired sleeping state" }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<SleepState> { description = "Updated sleeping state" }
            }
            HttpStatusCode.NotFound to {
                description = "Session or agent not found"
                body<RouteException> { description = "Error details" }
            }
        }
    }) { sleeping ->
        val body = call.receive<SleepState>()
        val result = setSleepingState(localSessionManager, sleeping.sessionId, sleeping.agentId, body.sleeping)
        call.respond(HttpStatusCode.OK, result)
    }
    

    // TODO: This POST endpoint duplicates the PUT behavior. Not strictly REST-compliant,
    //       but provided because custom tools can only issue POST requests.
    post<Sleeping>({
        summary = "Set agent sleeping state (POST)"
        description = "Same behavior as PUT: sets whether a given agent instance in a session is sleeping"
        operationId = "setAgentSleepingPost"
        request {
            body<SleepState> { description = "Desired sleeping state" }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<SleepState> { description = "Updated sleeping state" }
            }
            HttpStatusCode.NotFound to {
                description = "Session or agent not found"
                body<RouteException> { description = "Error details" }
            }
        }
    }) { sleeping ->
        val body = call.receive<SleepState>()
        val result = setSleepingState(localSessionManager, sleeping.sessionId, sleeping.agentId, body.sleeping)
        call.respond(HttpStatusCode.OK, result)
    }
}

private fun setSleepingState(
    localSessionManager: LocalSessionManager,
    sessionId: String,
    agentId: String,
    desiredSleeping: Boolean
): SleepState {
    val session = localSessionManager.getSession(sessionId)
        ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")
    if (!session.setAgentSleeping(agentId, desiredSleeping)) {
        throw RouteException(HttpStatusCode.NotFound, "Agent not found in session")
    }
    return SleepState(session.isAgentSleeping(agentId))
}
