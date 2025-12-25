package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.delete
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.routes.ApiV1
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.coralserver.session.SessionId
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.session.models.SessionRequest
import org.coralprotocol.coralserver.session.state.SessionState
import org.koin.ktor.ext.inject

private val logger = KotlinLogging.logger {}

@Serializable
data class BasicNamespace(
    val namespace: String,
    val sessions: List<BasicSession>
)

@Serializable
data class BasicSession(
    val sessionId: SessionId,
    val closing: Boolean
)

@Resource("sessions")
class Sessions(val parent: ApiV1 = ApiV1()) {
    @Resource("{namespace}")
    class WithNamespace(val parent: Sessions = Sessions(), val namespace: String) {

        @Resource("{sessionId}")
        class Session(val parent: WithNamespace, val sessionId: SessionId)
    }
}

/**
 * Configures session-related routes.
 */
fun Route.sessionApi() {
    val registry by inject<AgentRegistry>()
    val localSessionManager by inject<LocalSessionManager>()

    post<Sessions.WithNamespace>({
        summary = "Create session"
        description = "Creates a new session in a given namespace"
        operationId = "createSession"
        securitySchemeNames("token")
        request {
            body<SessionRequest> {
                description = "The session request body, containing the agents to use in the session and other settings"
            }
            pathParameter<String>("namespace") {
                description =
                    "The namespace this session should be created in.  This namespace will be created if it does not exist."
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
        val sessionRequest = call.receive<SessionRequest>()
        val agentGraph = sessionRequest.agentGraphRequest.toAgentGraph(registry)

        val (session, _) = localSessionManager.createAndLaunchSession(
            it.namespace,
            agentGraph,
            sessionRequest.sessionRuntimeSettings
        )

        call.respond(
            SessionIdentifier(
                sessionId = session.id,
                namespace = it.namespace
            )
        )

        logger.info { "Created new session ${session.id}" }
    }

    get<Sessions.WithNamespace>({
        summary = "List sessions in namespace"
        description = "Returns a list of all sessions in a specific namespace"
        operationId = "getSessionsInNamespace"
        securitySchemeNames("token")
        request {
            pathParameter<String>("namespace") {
                description = "The namespace to list sessions from"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<BasicSession>> {
                    description = "List of session IDs"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Invalid namespace provided"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { path ->
        try {
            call.respond(localSessionManager.getSessions(path.namespace).map { it.id })
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    get<Sessions>({
        summary = "Get all sessions from all namespaces"
        description = "Returns a list of namespaces containing their respective sessions"
        operationId = "getAllSessions"
        securitySchemeNames("token")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<BasicNamespace>> {
                    description = "List of session IDs"
                }
            }
        }
    }) {
        call.respond(localSessionManager.getNamespaces().map { namespace ->
            BasicNamespace(namespace.name, namespace.sessions.values.map {
                BasicSession(it.id, it.closing)
            })
        })
    }

    delete<Sessions.WithNamespace.Session>({
        summary = "Close an active session"
        description = "Closes an active session, cancelling all running agents"
        operationId = "closeSession"
        securitySchemeNames("token")
        request {
            pathParameter<String>("namespace") {
                description = "The namespace of the session to close"
            }

            pathParameter<String>("sessionId") {
                description = "The sessionId of the session to close"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
            HttpStatusCode.NotFound to {
                description = "If either namespace or session ID is invalid"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { path ->
        try {
            val namespace = localSessionManager.getSessions(path.parent.namespace)
            val session = namespace.find { it.id == path.sessionId }
                ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

            session.cancelAndJoinAgents()
            call.respond(HttpStatusCode.OK)
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    get<Sessions.WithNamespace.Session>({
        summary = "Get session state"
        description = "Returns the current state of a session, the agents, threads and their messages"
        operationId = "getSessionState"
        securitySchemeNames("token")
        request {
            pathParameter<String>("namespace") {
                description = "The namespace of the session"
            }

            pathParameter<String>("sessionId") {
                description = "The sessionId of the session"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<SessionState> {
                    description = "The session state"
                }
            }
            HttpStatusCode.NotFound to {
                description = "If either namespace or session ID is invalid"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { path ->
        try {
            val namespace = localSessionManager.getSessions(path.parent.namespace)
            val session = namespace.find { it.id == path.sessionId }
                ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

            call.respond(HttpStatusCode.OK, session.getState())
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }
}