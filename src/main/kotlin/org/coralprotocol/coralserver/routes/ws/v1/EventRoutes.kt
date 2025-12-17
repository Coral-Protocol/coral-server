package org.coralprotocol.coralserver.routes.ws.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.subscribe
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.routes.WsV1
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.coralserver.util.toWsFrame

@Resource("events/{token}")
class Events(val parent: WsV1 = WsV1(), val token: String) {
    @Resource("session/{namespace}/{sessionId}")
    class SessionEvents(val parent: Events, val namespace: String, val sessionId: String)

    @Resource("server")
    class ServerEvents(val parent: Events)
}

fun Route.eventRoutes(config: Config, localSessionManager: LocalSessionManager) {
    get<Events.SessionEvents>({
        hidden = true
    }) { path ->
        if (!config.auth.keys.contains(path.parent.token))
            throw RouteException(HttpStatusCode.Unauthorized, "Invalid token")

        val session = try {
            val namespace = localSessionManager.getSessions(path.namespace)
            namespace.find { it.id == path.sessionId }
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
}