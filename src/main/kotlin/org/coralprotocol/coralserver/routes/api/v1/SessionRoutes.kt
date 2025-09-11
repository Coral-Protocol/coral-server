package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.session.models.SessionRequest

private val logger = KotlinLogging.logger {}

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> =
    filterValues { it != null } as Map<K, V>

@Resource("/api/v1/sessions")
class Sessions

/**
 * Configures session-related routes.
 */
fun Routing.sessionApiRoutes(
    registry: AgentRegistry,
    localSessionManager: LocalSessionManager,
    devMode: Boolean
) {
    post<Sessions>({
        summary = "Create session"
        description = "Creates a new session"
        operationId = "createSession"
        request {
            body<SessionRequest> {
                description = "Session creation request"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<SessionIdentifier> {
                    description = "Session details"
                }
            }
            HttpStatusCode.Forbidden to {
                description = "Invalid application ID or privacy key"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "The agent graph is invalid and could not be processed"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) {
        val request = call.receive<SessionRequest>()
        val agentGraph = request.agentGraphRequest.toAgentGraph(registry)

        // TODO(alan): actually limit agent communicating using AgentGraph.groups
        val session = if (request.sessionId != null && devMode) {
            localSessionManager.createSessionWithId(
                request.sessionId,
                request.applicationId,
                request.privacyKey,
                agentGraph
            )
        }
        else {
            localSessionManager.createSession(request.applicationId, request.privacyKey, agentGraph)
        }

        call.respond(
            SessionIdentifier(
                sessionId = session.id,
                applicationId = session.applicationId,
                privacyKey = session.privacyKey
            )
        )

        logger.info { "Created new session ${session.id} for application ${session.applicationId}" }
    }

    // TODO: this should probably be protected (only for debug maybe)
    get<Sessions>({
        summary = "Get sessions"
        description = "Fetches all active session IDs"
        operationId = "getSessions"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<String>> {
                    description = "List of session IDs"
                }
            }
        }
    }) {
        val sessions = localSessionManager.getAllSessions()
        call.respond(HttpStatusCode.OK, sessions.map { it.id })
    }
}