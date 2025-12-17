package org.coralprotocol.coralserver.routes.api.v1

import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.routes.ApiV1
import org.coralprotocol.coralserver.server.AuthSession
import org.coralprotocol.coralserver.server.RouteException

@Resource("auth")
class Auth(val parent: ApiV1 = ApiV1()) {

    @Resource("token/{token}")
    class Token(val parent: Auth = Auth(), val token: String)
}

fun Route.authApi(config: Config) {
    post<Auth.Token>({
        hidden = true
    }) { path ->
        if (!config.auth.keys.contains(path.token))
            throw RouteException(HttpStatusCode.Unauthorized, "Invalid token")

        call.sessions.set(AuthSession.Token(path.token))
        call.respondRedirect(call.parameters["redirect_to"] ?: "/ui/console/")
    }
}