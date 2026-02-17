package org.coralprotocol.coralserver.routes.mcp.v1

import io.github.smiley4.ktoropenapi.documentation
import io.github.smiley4.ktoropenapi.method
import io.github.smiley4.ktoropenapi.resources.delete
import io.github.smiley4.ktoropenapi.resources.extractTypesafeDocumentation
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.Resources
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.serializer
import org.coralprotocol.coralserver.routes.McpV1
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionAgentSecret
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.coralserver.util.MCP_SESSION_ID_HEADER_NAME
import org.coralprotocol.coralserver.util.StreamableHttpServerTransport
import org.koin.ktor.ext.inject
import java.util.*
import kotlin.time.Duration.Companion.minutes

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
                                agentLocator.agent.connectTransport(
                                    SseServerTransport(
                                        endpoint = "",
                                        session = this
                                    )
                                )

                                awaitCancellation()
                            })
                        } catch (_: SessionException.InvalidAgentSecret) {
                            call.respond(HttpStatusCode.Unauthorized)
                        }
                    }
                }
            }
        }
    }

    post<Sse>({
        hidden = true
    }) {
        try {
            val agentLocator = localSessionManager.locateAgent(it.agentSecret)
            agentLocator.agent.handleSsePostMessage(call)
        } catch (_: SessionException.InvalidAgentSecret) {
            throw RouteException(HttpStatusCode.Unauthorized, "Invalid agent secret")
        }
    }

    post<StreamableHttp>({
        hidden = true
    }) {
        try {
            val agent = localSessionManager.locateAgent(it.agentSecret).agent
            val sessionId = call.request.header(MCP_SESSION_ID_HEADER_NAME)

            val transport =
                if (sessionId != null) {
                    agent.findMcpTransport(sessionId)
                } else {
                    val transportSessionId = UUID.randomUUID().toString();
                    agent.connectTransport(
                        StreamableHttpServerTransport(
                            messageQueueCapacity = 4096,
                            responseTimeout = 2.minutes,
                            transportSessionId = transportSessionId,
                            logger = agent.logger
                        ), transportSessionId
                    )
                }

            transport.handlePost(call)
        } catch (_: SessionException.InvalidAgentSecret) {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }

    get<StreamableHttp>({
        hidden = true
    }) {
        try {
            val agent = localSessionManager.locateAgent(it.agentSecret).agent
            val sessionId =
                call.request.header(MCP_SESSION_ID_HEADER_NAME) ?: throw RouteException(HttpStatusCode.BadRequest)
            agent.findMcpTransport<StreamableHttpServerTransport>(sessionId).sseStream(call)

        } catch (_: SessionException.InvalidAgentSecret) {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }

    delete<StreamableHttp>({
        hidden = true
    }) {
        try {
            val agent = localSessionManager.locateAgent(it.agentSecret).agent
            val sessionId =
                call.request.header(MCP_SESSION_ID_HEADER_NAME) ?: throw RouteException(HttpStatusCode.BadRequest)
            agent.findMcpTransport<StreamableHttpServerTransport>(sessionId).close()
        } catch (_: SessionException.InvalidAgentSecret) {
            call.respond(HttpStatusCode.Unauthorized)
        }
    }
}

private inline fun <reified T> SessionAgent.findMcpTransport(sessionId: String): T
        where T : AbstractTransport {
    val transport = mcpSessions[sessionId]?.transport ?: throw RouteException(
        HttpStatusCode.NotFound,
        "Session $sessionId not found"
    )

    return transport as? T
        ?: throw RouteException(
            HttpStatusCode.NotFound,
            "session $sessionId is of the wrong type"
        )
}