package org.coralprotocol.coralserver.routes.ws.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.config.AuthConfig
import org.coralprotocol.coralserver.config.LoggingConfig
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.logging.LoggingTagFilter
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.routes.WsV1
import org.coralprotocol.coralserver.server.AuthSession
import org.coralprotocol.coralserver.util.toWsFrame
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject

@Resource("logs")
class Logs(
    val parent: WsV1 = WsV1(),
    val namespaceFilter: String? = null,
    val sessionFilter: String? = null,
    val agentFilter: String? = null,
    val allowSensitive: Boolean = false,
    val limit: Int = 1024,
) {
    @Resource("{token}")
    class WithToken(
        val parent: Logs = Logs(),
        val token: String
    )
}

fun Route.logRoutes() {
    val logger by inject<Logger>()
    val authConfig by inject<AuthConfig>()
    val loggingConfig by inject<LoggingConfig>()
    val json by inject<Json>()
    val websocketCoroutineScope by inject<CoroutineScope>(named("websocketCoroutineScope"))

    suspend fun RoutingContext.handleLogs(loggingTagFilter: LoggingTagFilter, limit: Int) {
        val limit = limit.coerceAtMost(loggingConfig.maxReplay.toInt())

        call.respond(WebSocketUpgrade(call) {
            logger.flow
                .drop((logger.flow.replayCache.size - limit).coerceAtLeast(0))
                .filter {
                    loggingTagFilter.filter(it)
                }.onEach {
                    outgoing.send(it.toWsFrame(json))
                }
                .launchIn(websocketCoroutineScope)
                .join()
        })
    }

    get<Logs.WithToken>({
        hidden = true
    }) { path ->
        if (!authConfig.keys.contains(path.token))
            throw RouteException(HttpStatusCode.Unauthorized, "Invalid token")

        handleLogs(
            LoggingTagFilter(
                namespaceFilter = path.parent.namespaceFilter,
                sessionFilter = path.parent.sessionFilter,
                agentFilter = path.parent.agentFilter,
                allowSensitive = path.parent.allowSensitive
            ), path.parent.limit
        )
    }

    get<Logs>({
        hidden = true
    }) { path ->
        if (call.sessions.get<AuthSession.Token>() == null)
            throw RouteException(HttpStatusCode.Unauthorized, "Unauthorized")

        handleLogs(
            LoggingTagFilter(
                namespaceFilter = path.namespaceFilter,
                sessionFilter = path.sessionFilter,
                agentFilter = path.agentFilter,
                allowSensitive = path.allowSensitive
            ), path.limit
        )
    }
}