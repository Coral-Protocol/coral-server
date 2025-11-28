package org.coralprotocol.coralserver.routes.sse.v1

import io.github.smiley4.ktoropenapi.documentation
import io.github.smiley4.ktoropenapi.method
import io.github.smiley4.ktoropenapi.resources.extractTypesafeDocumentation
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.resources.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.serialization.serializer
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionAgentSecret
import org.coralprotocol.coralserver.session.SessionException

/**
 * Some agent frameworks identify that a connection is SSE by the presence of the trailing /sse.  This is obviously bad,
 * but we aim to support as many frameworks as possible, so the route will be named this way.  It makes the full naming
 * for this route a little awkward (https://x.x.x.x:5555/sse/v1/mcp/{secret}/sse).  Agents should not be entering this
 * URL in manually anyway.
 */
@Resource("mcp")
class Mcp() {
    /**
     * This path NEEDS the trailing slash, or else Anthropic in their infinite wisdom decide that the /sse part of this
     * should be stripped off when constructing a base URL (in the MCP Kotlin SDK).
     */
    @Resource("{agentSecret}/sse/")
    class Sse(val agentSecret: SessionAgentSecret)
}

fun Route.mcpRoutes(sessionManager: LocalSessionManager) {
    val resources = plugin(Resources)
    val extractedDocumentation = extractTypesafeDocumentation(serializer<Mcp>(), resources.resourcesFormat)
    documentation(extractedDocumentation) {
        documentation({
            hidden = true
        }) {
            resource<Mcp.Sse> {
                method(HttpMethod.Get) {
                    val serializer = serializer<Mcp.Sse>()
                    handle(serializer) {
                        try {
                            val agentLocator = sessionManager.locateAgent(it.agentSecret)

                            call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                            call.response.header(HttpHeaders.CacheControl, "no-store")
                            call.response.header(HttpHeaders.Connection, "keep-alive")
                            call.response.header("X-Accel-Buffering", "no")
                            call.respond(SSEServerContent(call) {
                                agentLocator.agent.connectSseSession(this)
                            })
                        }
                        catch (_: SessionException.InvalidAgentSecret) {
                            call.respond(HttpStatusCode.Unauthorized)
                        }
                    }
                }
            }
        }
    }

    post<Mcp.Sse>({
        hidden = true
    }) {
        try {
            val agentLocator = sessionManager.locateAgent(it.agentSecret)
            agentLocator.agent.handlePostMessage(call)
        }
        catch (_: SessionException.InvalidAgentSecret) {
            throw RouteException(HttpStatusCode.Unauthorized, "Invalid agent secret")
        }
    }
}