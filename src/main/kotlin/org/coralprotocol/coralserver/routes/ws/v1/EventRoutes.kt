package org.coralprotocol.coralserver.routes.ws.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.websocket.*
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.routes.WsV1
import org.coralprotocol.coralserver.server.AuthSession
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.coralserver.session.SessionId
import org.coralprotocol.coralserver.util.toWsFrame

@Resource("events")
class Events(val parent: WsV1 = WsV1()) {

    @Resource("{token}")
    class WithToken(val parent: Events = Events(), val token: String) {
        @Resource("session/{namespace}/{sessionId}")
        class SessionEvents(val parent: WithToken, val namespace: String, val sessionId: String)

        @Resource("server")
        class ServerEvents(val parent: WithToken)
    }

    @Resource("session/{namespace}/{sessionId}")
    class SessionEvents(val parent: Events = Events(), val namespace: String, val sessionId: String)

    @Resource("server")
    class ServerEvents(val parent: Events)
}

fun Route.eventRoutes(config: Config, localSessionManager: LocalSessionManager) {
    suspend fun RoutingContext.handleSessionEvents(namespace: String, sessionId: SessionId) {
        val session = try {
            val namespace = localSessionManager.getSessions(namespace)
            namespace.find { it.id == sessionId }
                ?: throw RouteException(HttpStatusCode.NotFound, "Session not found")
        } catch (e: SessionException.InvalidNamespace) {
            throw RouteException(HttpStatusCode.NotFound, e)
        }

        call.respond(WebSocketUpgrade(call) {
            session.events.consumeAsFlow().onEach {
                outgoing.send(it.toWsFrame())
            }.launchIn(session.sessionScope).join()
        })
    }

    get<Events.WithToken.SessionEvents>({
        hidden = true
    }) { path ->
        if (!config.auth.keys.contains(path.parent.token))
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
}