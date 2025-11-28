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

@Resource("sessions")
class Sessions() {
    @Resource("{namespace}")
    class WithNamespace(val namespace: String)
}

/**
 * Configures session-related routes.
 */
fun Route.sessionApiRoutes(
    registry: AgentRegistry,
    localSessionManager: LocalSessionManager,
    devMode: Boolean
) {
    post<Sessions.WithNamespace>({
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

        val session = localSessionManager.createSession(it.namespace, agentGraph)

        call.respond(
            SessionIdentifier(
                sessionId = session.id,
                namespace = it.namespace
            )
        )

        logger.info { "Created new session ${session.id}" }
    }

    get<Sessions>({
        summary = "Get all sessions"
        description = "Returns a list of all sessions from all namespaces"
        operationId = "getAllSessions"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<String>> {
                    description = "List of session IDs"
                }
            }
        }
    }) {
        TODO()
    }

    get<Sessions.WithNamespace>({
        summary = "Get sessions in a namespace"
        description = "Returns a list of all sessions in a specific namespace"
        operationId = "getSessionsInNamespace"
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<String>> {
                    description = "List of session IDs"
                }
            }
        }
    }) {
        TODO()
    }
}