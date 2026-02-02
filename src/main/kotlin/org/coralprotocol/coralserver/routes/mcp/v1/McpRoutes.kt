package org.coralprotocol.coralserver.routes.mcp.v1

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
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.serialization.serializer
import org.coralprotocol.coralserver.routes.McpV1
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionAgentSecret
import org.coralprotocol.coralserver.session.SessionException
import org.koin.ktor.ext.inject

/**
 * This path NEEDS the trailing slash, or else Anthropic in their infinite wisdom decide that the /sse part of this
 * should be stripped off when constructing a base URL (in the MCP Kotlin SDK).
 */
@Resource("{agentSecret}/sse/")
class Sse(val parent: McpV1 = McpV1(), val agentSecret: SessionAgentSecret)

@Resource("{agentSecret}/mcp")
class StreamableHttp(val parent: McpV1 = McpV1(), val agentSecret: SessionAgentSecret)

fun Route.mcpRoutes() {
    val localSessionManager by inject<LocalSessionManager>()

    val resources = plugin(Resources)
    val extractedDocumentation = extractTypesafeDocumentation(serializer<Sse>(), resources.resourcesFormat)
    documentation(extractedDocumentation) {
        documentation({
            hidden = true
        }) {
            resource<Sse> {
                method(HttpMethod.Get) {
                    val serializer = serializer<Sse>()
                    handle(serializer) {
                        try {
                            val agentLocator = localSessionManager.locateAgent(it.agentSecret)

                            call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                            call.response.header(HttpHeaders.CacheControl, "no-store")
                            call.response.header(HttpHeaders.Connection, "keep-alive")
                            call.response.header("X-Accel-Buffering", "no")
                            call.respond(SSEServerContent(call) {
                                agentLocator.agent.connectMcpTransport(
                                    SseServerTransport(
                                        endpoint = "",
                                        session = this
                                    )
                                )
                            })
                        } catch (_: SessionException.InvalidAgentSecret) {
                            call.respond(HttpStatusCode.Unauthorized)
                        }
                    }
                }
            }
        }
    }

    suspend fun RoutingContext.postMessage(secret: SessionAgentSecret) {
        try {
            val agentLocator = localSessionManager.locateAgent(secret)
            agentLocator.agent.handlePostMessage(call)
        } catch (_: SessionException.InvalidAgentSecret) {
            throw RouteException(HttpStatusCode.Unauthorized, "Invalid agent secret")
        }
    }

    post<Sse>({
        hidden = true
    }) { postMessage(it.agentSecret) }

    post<StreamableHttp>({
        hidden = true
    }) { postMessage(it.agentSecret) }
}