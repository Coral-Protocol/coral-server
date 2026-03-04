package org.coralprotocol.coralserver.routes.ws.v1

import io.github.smiley4.ktoropenapi.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.config.AuthConfig
import org.coralprotocol.coralserver.config.LoggingConfig
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.logging.LoggingTagFilter
import org.coralprotocol.coralserver.modules.LOGGER_LOG_API
import org.coralprotocol.coralserver.modules.LOGGER_ROUTES
import org.coralprotocol.coralserver.modules.WEBSOCKET_COROUTINE_SCOPE_NAME
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.routes.WsV1
import org.coralprotocol.coralserver.server.AuthSession
import org.coralprotocol.coralserver.util.toWsFrame
import org.coralprotocol.coralserver.util.webSocketFlow
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
    val logApiLogger by inject<Logger>(named(LOGGER_LOG_API))
    val routeLogger by inject<Logger>(named(LOGGER_ROUTES))

    val authConfig by inject<AuthConfig>()
    val loggingConfig by inject<LoggingConfig>()
    val websocketCoroutineScope by inject<CoroutineScope>(named(WEBSOCKET_COROUTINE_SCOPE_NAME))
    val json by inject<Json>()

    suspend fun RoutingContext.handleLogs(loggingTagFilter: LoggingTagFilter, limit: Int) {
        val limit = limit.coerceAtMost(loggingConfig.maxReplay.toInt())

        webSocketFlow("logs", routeLogger, websocketCoroutineScope) {
            // count the number of elements that will be replayed that much the filter, if this is more than the
            // requested limit then some must be replayed events must be dropped
            val numFiltered = logApiLogger.flow.replayCache.count { loggingTagFilter.filter(it) }

            logApiLogger.flow
                .filter {
                    loggingTagFilter.filter(it)
                }
                .drop((numFiltered - limit).coerceAtLeast(0)) // drop must occur after filtration
                .onEach {
                    outgoing.send(it.toWsFrame(json))
                }
                .catch {
                    if (it !is ClosedWriteChannelException)
                        logApiLogger.error(it) { "unexpected logs ws error" }
                }
        }
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