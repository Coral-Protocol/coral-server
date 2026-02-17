package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.delete
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_ROUTES
import org.coralprotocol.coralserver.routes.ApiV1
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.*
import org.coralprotocol.coralserver.session.state.SessionNamespaceState
import org.coralprotocol.coralserver.session.state.SessionStateBase
import org.coralprotocol.coralserver.session.state.SessionStateExtended
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

@Resource("local")
class LocalSessions(val parent: ApiV1 = ApiV1()) {
    @Resource("session")
    class Session(val parent: LocalSessions = LocalSessions()) {
        @Resource("{namespace}/{sessionId}")
        class Existing(val parent: Session = Session(), val namespace: String, val sessionId: String)
    }

    @Resource("namespace")
    class Namespace(val parent: LocalSessions = LocalSessions()) {
        @Resource("{namespace}")
        class Existing(val parent: Namespace = Namespace(), val namespace: String)
    }
}

/**
 * Configures session-related routes.
 */
fun Route.localSessionApi() {
    val registry by inject<AgentRegistry>()
    val localSessionManager by inject<LocalSessionManager>()
    val logger by inject<Logger>(named(LOGGER_ROUTES))

    post<LocalSessions.Session>({
        summary = "Create session"
        description = "Creates a new session in a given namespace"
        operationId = "createSession"
        securitySchemeNames("token")
        request {
            body<SessionRequest> {
                description = "The session request body, containing the agents to use in the session and other settings"
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
    }) { _ ->
        val sessionRequest = call.receive<SessionRequest>()
        val agentGraph = sessionRequest.agentGraphRequest.toAgentGraph(registry)

        val existingNamespaces = localSessionManager.getNamespaces()
        val namespace = when (sessionRequest.namespaceProvider) {
            is SessionNamespaceProvider.CreateIfNotExists -> {
                existingNamespaces.firstOrNull { it.name == sessionRequest.namespaceProvider.namespaceRequest.name }
                    ?: localSessionManager.createNamespace(sessionRequest.namespaceProvider.namespaceRequest)
            }

            is SessionNamespaceProvider.UseExisting -> {
                existingNamespaces.firstOrNull { it.name == sessionRequest.namespaceProvider.name }
                    ?: throw RouteException(HttpStatusCode.NotFound, "Namespace not found")
            }
        }

        val (session, _) = localSessionManager.createSession(
            namespace,
            agentGraph,
            sessionRequest.annotations
        )

        when (sessionRequest.execution) {
            is SessionRequestExecution.Defer -> {
                logger.info { "session \"${session.id}\" was created in \"${namespace.name}\" with deferred execution" }
            }

            is SessionRequestExecution.Execute -> {
                logger.info { "session \"${session.id}\" was created in \"${namespace.name}\" with immediate execution" }
                localSessionManager.launchSession(session, namespace, sessionRequest.execution.runtimeSettings)
            }
        }

        call.respond(
            SessionIdentifier(
                sessionId = session.id,
                namespace = namespace.name
            )
        )
    }

    post<LocalSessions.Namespace>({
        summary = "Create namespace"
        description = "Creates a new empty namespace"
        operationId = "createNamespace"
        securitySchemeNames("token")
        request {
            body<SessionNamespaceRequest> {
                description = "Namespace settings"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
            }
            HttpStatusCode.Forbidden to {
                description = "Invalid application ID or privacy key"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "Invalid namespace settings providewd"
                body<RouteException> {
                    description = "Exact error message and stack trace"
                }
            }
        }
    }) {
        try {
            localSessionManager.createNamespace(call.receive<SessionNamespaceRequest>())
            call.respond(HttpStatusCode.OK)
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.BadRequest, e)
        }
    }

    get<LocalSessions.Namespace.Existing>({
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
                body<List<SessionStateBase>> {
                    description = "A list of session states"
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
            call.respond(localSessionManager.getSessions(path.namespace).map { it.getState().base })
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    delete<LocalSessions.Namespace.Existing>({
        summary = "Delete a namespace"
        description = "Deletes a given namespace, closing any session that it may contain"
        operationId = "deleteNamespace"
        securitySchemeNames("token")
        request {
            pathParameter<String>("namespace") {
                description = "The namespace to delete"
            }
        }
        response {
            HttpStatusCode.NotFound to {
                description = "Invalid namespace provided"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { path ->
        try {
            localSessionManager.deleteNamespace(path.namespace)
            call.respond(HttpStatusCode.OK)
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    get<LocalSessions>({
        summary = "Get a list of namespace states and their contained sessions states"
        description = "Returns a list of namespace states and their contained sessions states"
        operationId = "getAllSessions"
        securitySchemeNames("token")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<SessionNamespaceState>> {
                    description = "List of namespace states, containing their sessions' states"
                }
            }
        }
    }) {
        call.respond(localSessionManager.getNamespaceStates())
    }

    get<LocalSessions.Namespace>({
        summary = "Get a list of namespaces names"
        description = "Returns a list of namespaces"
        operationId = "getAllNamespaceNames"
        securitySchemeNames("token")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<String>> {
                    description = "List of namespace names"
                }
            }
        }
    }) {
        call.respond(localSessionManager.getNamespaces().map { it.name })
    }

    delete<LocalSessions.Session.Existing>({
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
            val namespace = localSessionManager.getSessions(path.namespace)
            val session = namespace.find { it.id == path.sessionId }
                ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

            session.cancelAndJoinAgents()
            call.respond(HttpStatusCode.OK)
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    get<LocalSessions.Session.Existing>({
        summary = "Get extended session state"
        description = "Returns a session's state, extended with: agents, threads and thread messages"
        operationId = "getExtendedSessionState"
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
                body<SessionStateExtended> {
                    description = "Extended session state"
                }
            }
            HttpStatusCode.NotFound to {
                description = "Either namespace or session ID is invalid"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { path ->
        try {
            val namespace = localSessionManager.getSessions(path.namespace)
            val session = namespace.find { it.id == path.sessionId }
                ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

            call.respond(HttpStatusCode.OK, session.getState())
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    post<LocalSessions.Session.Existing>({
        summary = "Executes a session"
        description = "Executes a session was created with deferred execution"
        operationId = "executeDeferredSession"
        securitySchemeNames("token")
        request {
            body<SessionRuntimeSettings> {
                description = "Settings for the execution of the session"
            }
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
            }
            HttpStatusCode.NotFound to {
                description = "Either namespace or session ID is invalid"
                body<RouteException> {
                    description = "Error message"
                }
            }
            HttpStatusCode.BadRequest to {
                description = "The session exists but is not pending execution"
                body<RouteException> {
                    description = "Error message"
                }
            }
        }
    }) { path ->
        try {
            val namespace = localSessionManager.getNamespace(path.namespace)
            val session = namespace.sessions[path.sessionId]
                ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

            if (session.status.value is SessionStatus.PendingExecution) {
                localSessionManager.launchSession(session, namespace, call.receive())
            } else
                throw RouteException(HttpStatusCode.BadRequest, "Session is not pending execution")

            call.respond(HttpStatusCode.OK)
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }
}