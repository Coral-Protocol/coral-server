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
import org.coralprotocol.coralserver.session.state.SessionNamespaceStateBase
import org.coralprotocol.coralserver.session.state.SessionNamespaceStateExtended
import org.coralprotocol.coralserver.session.state.SessionStateBase
import org.coralprotocol.coralserver.session.state.SessionStateExtended
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

@Resource("local")
class LocalSessions(val parent: ApiV1 = ApiV1()) {
    @Resource("session")
    class Session(val parent: LocalSessions = LocalSessions()) {
        @Resource("{namespace}/{sessionId}")
        class Existing(val parent: Session = Session(), val namespace: String, val sessionId: String) {
            @Resource("extended")
            class Extended(val parent: Existing)
        }
    }

    @Resource("namespace")
    class Namespace(val parent: LocalSessions = LocalSessions()) {
        @Resource("{namespace}")
        class Existing(val parent: Namespace = Namespace(), val namespace: String) {
            @Resource("extended")
            class Extended(val parent: Existing)
        }

        @Resource("extended")
        class Extended(val parent: Namespace = Namespace())
    }
}

suspend fun resolveAndLaunchSession(
    sessionRequest: SessionRequest,
    agentRegistry: AgentRegistry,
    localSessionManager: LocalSessionManager,
    logger: Logger,
    logPrefix: String = "session",
): SessionIdentifier {
    val agentGraph = sessionRequest.agentGraphRequest.toAgentGraph(agentRegistry)

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
            logger.info { "$logPrefix \"${session.id}\" created in \"${namespace.name}\" with deferred execution" }
        }

        is SessionRequestExecution.Execute -> {
            logger.info { "$logPrefix \"${session.id}\" created in \"${namespace.name}\" with immediate execution" }
            localSessionManager.launchSession(session, namespace, sessionRequest.execution.runtimeSettings)
        }
    }

    return SessionIdentifier(
        sessionId = session.id,
        namespace = namespace.name,
    )
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
        call.respond(resolveAndLaunchSession(sessionRequest, registry, localSessionManager, logger))
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

    get<LocalSessions.Namespace.Existing>({
        summary = "List base session states"
        description = "List base session states for a given namespace"
        operationId = "getSessionStates"
        securitySchemeNames("token")
        request {
            pathParameter<String>("namespace") {
                description = "The namespace name"
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


    get<LocalSessions.Namespace.Existing.Extended>({
        summary = "List extended session states"
        description = "List extended session states for a given namespace"
        operationId = "getSessionStatesExtended"
        securitySchemeNames("token")
        request {
            pathParameter<String>("namespace") {
                description = "The namespace name"
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<SessionStateExtended>> {
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
            call.respond(localSessionManager.getSessions(path.parent.namespace).map { it.getState() })
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    get<LocalSessions.Namespace>({
        summary = "List base namespace states"
        description = "Returns a list of namespace states"
        operationId = "getNamespaceStates"
        securitySchemeNames("token")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<SessionNamespaceStateBase>> {
                    description = "List of namespace states"
                }
            }
        }
    }) {
        call.respond(localSessionManager.getNamespaceStates().map { it.base })
    }

    get<LocalSessions.Namespace.Extended>({
        summary = "List extended namespace states"
        description = "Returns a list of extended namespace states"
        operationId = "getNamespaceStatesExtended"
        securitySchemeNames("token")
        response {
            HttpStatusCode.OK to {
                description = "Success"
                body<List<SessionNamespaceStateExtended>> {
                    description = "List of extended namespace states"
                }
            }
        }
    }) {
        call.respond(localSessionManager.getNamespaceStates())
    }

    get<LocalSessions.Session.Existing>({
        summary = "Get base session state"
        description = "Returns a session's state"
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
                body<SessionStateBase> {
                    description = "Base session state"
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

            call.respond(HttpStatusCode.OK, session.getState().base)
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }
    }

    get<LocalSessions.Session.Existing.Extended>({
        summary = "Get extended session state"
        description = "Returns a session's extended state"
        operationId = "getSessionStateExtended"
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
            val namespace = localSessionManager.getSessions(path.parent.namespace)
            val session = namespace.find { it.id == path.parent.sessionId }
                ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")

            call.respond(HttpStatusCode.OK, session.getState())
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
}