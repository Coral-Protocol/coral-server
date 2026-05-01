package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.coralprotocol.coralserver.config.AuthConfig
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_ROUTES
import org.coralprotocol.coralserver.routes.ApiV1
import org.coralprotocol.coralserver.server.AuthSession
import org.coralprotocol.coralserver.routes.RouteException
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import kotlin.getValue

@Resource("auth")
class Auth(val parent: ApiV1 = ApiV1()) {

    @Resource("token")
    class Token(val parent: Auth = Auth())
}

fun Route.authApi() {
    val logger by inject<Logger>(named(LOGGER_ROUTES))
    val config by inject<AuthConfig>()

    if (config.keys.isEmpty()) {
        buildString {
            appendLine()
            appendLine("=".repeat(60))
            appendLine("No auth keys are configured. Authenticated routes will not be accessible.")
            appendLine("(most things will not work)")
            appendLine("To fix this, set auth.keys via the config file or via --auth.keys=...")
            appendLine("=".repeat(60))
            appendLine()
        }
    }

    post<Auth.Token>({
        hidden = true
    }) { path ->
        val token = call.receiveParameters()["token"]
        if (token == null || !config.keys.contains(token))
            throw RouteException(HttpStatusCode.Unauthorized, "Invalid token")

        call.sessions.set(AuthSession.Token(token))
        call.respondRedirect(call.parameters["to"] ?: "/ui/console/")
    }
}