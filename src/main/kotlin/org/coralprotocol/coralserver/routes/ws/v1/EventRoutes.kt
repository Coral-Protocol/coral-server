package org.coralprotocol.coralserver.routes.ws.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.config.AuthConfig
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_LOG_API
import org.coralprotocol.coralserver.modules.WEBSOCKET_COROUTINE_SCOPE_NAME
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.routes.WsV1
import org.coralprotocol.coralserver.server.AuthSession
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.coralserver.session.SessionId
import org.coralprotocol.coralserver.util.toWsFrame
import org.coralprotocol.coralserver.util.webSocketFlow
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

@Resource("events")
class Events(
    val parent: WsV1 = WsV1(),
    val namespaceFilter: String? = null,
) {

    @Resource("{token}")
    class WithToken(val parent: Events = Events(), val token: String) {
        @Resource("session/{namespace}/{sessionId}")
        class SessionEvents(val parent: WithToken, val namespace: String, val sessionId: String)

        @Resource("lsm")
        class LsmEvents(val parent: WithToken)
    }

    @Resource("session/{namespace}/{sessionId}")
    class SessionEvents(val parent: Events = Events(), val namespace: String, val sessionId: String)

    @Resource("lsm")
    class LsmEvents(val parent: Events)
}

fun Route.eventRoutes() {
    val localSessionManager by inject<LocalSessionManager>()
    val config by inject<AuthConfig>()
    val json by inject<Json>()
    val websocketCoroutineScope by inject<CoroutineScope>(named(WEBSOCKET_COROUTINE_SCOPE_NAME))
    val logger by inject<Logger>(named(LOGGER_LOG_API))

    suspend fun RoutingContext.handleSessionEvents(namespace: String, sessionId: SessionId) {
        val session = try {
            val namespace = localSessionManager.getSessions(namespace)
            namespace.find { it.id == sessionId }
                ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }

        webSocketFlow("events", logger, session.sessionScope) {
            session.events
                .onEach { outgoing.send(it.toWsFrame(json)) }
                .catch {
                    logger.error(it) { "unexpected events ws error" }
                }
        }
    }

    suspend fun RoutingContext.handleServerEvents(namespaceFilter: String? = null) {
        webSocketFlow("events", logger, websocketCoroutineScope) {
            localSessionManager.events
                .filter {
                    namespaceFilter == null || it.namespace == namespaceFilter
                }
                .onEach { outgoing.send(it.toWsFrame(json)) }
                .catch {
                    logger.error(it) { "unexpected events ws error" }
                }
        }
    }

    get<Events.WithToken.SessionEvents>({
        hidden = true
    }) { path ->
        if (!config.keys.contains(path.parent.token))
            throw RouteException(HttpStatusCode.Unauthorized, "Invalid token")

        handleSessionEvents(path.namespace, path.sessionId)
    }

    get<Events.SessionEvents>({
        hidden = true
    }) { path ->
        if (call.sessions.get<AuthSession.Token>() == null)
            throw RouteException(HttpStatusCode.Unauthorized, "Unauthorized")

        handleSessionEvents(path.namespace, path.sessionId)
    }

    get<Events.WithToken.LsmEvents>({
        hidden = true
    }) { path ->
        if (!config.keys.contains(path.parent.token))
            throw RouteException(HttpStatusCode.Unauthorized, "Invalid token")

        handleServerEvents(path.parent.parent.namespaceFilter)
    }

    get<Events.LsmEvents>({
        hidden = true
    }) { path ->
        if (call.sessions.get<AuthSession.Token>() == null)
            throw RouteException(HttpStatusCode.Unauthorized, "Unauthorized")

        handleServerEvents(path.parent.namespaceFilter)
    }
}