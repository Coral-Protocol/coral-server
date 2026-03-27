package org.coralprotocol.coralserver.utils

import io.github.smiley4.ktoropenapi.resources.delete
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.kotest.matchers.concurrent.suspension.shouldCompleteWithin
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.PrototypeRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.agent.runtime.prototype.*
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.mcp.buildToolSchema
import org.coralprotocol.coralserver.modules.LOGGER_TEST
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.util.StreamableHttpServerTransport
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
data class TestToolInput(
    val input: String
)

@Resource("test-mcp-server")
class TestMcpResource {
    @Resource("no-auth")
    class NoAuth {
        @Resource("mcp")
        class Mcp(val parent: NoAuth = NoAuth())
    }

    @Resource("query-param-auth")
    class QueryParamAuth {
        @Resource("mcp")
        class Mcp(val parent: QueryParamAuth = QueryParamAuth(), val authToken: String)
    }

    @Resource("bearer-auth")
    class BearerAuth {
        @Resource("mcp")
        class Mcp(val parent: BearerAuth = BearerAuth())
    }
}

/**
 * A test MCP that can be used for testing prototype runtimes toolServer configuration.  Note that SSE is not used here
 * because of a kotlin-sdk bug where rejecting a client for having the incorrect authentication will result in the
 * client waiting for it's timeout before failing.
 */
class TestMcpServer(
    val toolName: String = "test_tool",
    val toolDescription: String = "Test tool",
) : KoinComponent {
    val toolChannel: Channel<String> = Channel()
    val json by inject<Json>()

    val server = Server(
        serverInfo = Implementation(
            name = "test-server", version = "1.0.0"
        ), options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false)
            )
        )
    ).apply {
        addTool(
            name = toolName,
            description = toolDescription,
            inputSchema = buildToolSchema<TestToolInput>(),
        ) { request ->
            try {
                toolChannel.send(
                    json.decodeFromJsonElement<TestToolInput>(
                        request.arguments ?: return@addTool CallToolResult(
                            content = listOf(TextContent("error: no input")), isError = true
                        )
                    ).input
                )
            } catch (e: SerializationException) {
                request.arguments ?: return@addTool CallToolResult(
                    content = listOf(TextContent("error: ${e.message}")), isError = true
                )
            }

            CallToolResult(content = listOf(TextContent("Success!")))
        }
    }

    suspend fun streamableHttpTransport(): StreamableHttpServerTransport {
        val transport = StreamableHttpServerTransport(
            messageQueueCapacity = 4096,
            responseTimeout = 2.minutes,
            transportSessionId = UUID.randomUUID().toString(),
            logger = get<Logger>(named(LOGGER_TEST))
        )
        server.createSession(transport)

        return transport
    }

    fun asPrototypeToolServer(application: Application): PrototypeToolServer {
        var transport: StreamableHttpServerTransport? = null
        application.routing {
            post<TestMcpResource.NoAuth.Mcp> {
                transport = streamableHttpTransport()
                transport!!.handlePost(call)
            }

            get<TestMcpResource.NoAuth.Mcp> {
                transport?.sseStream(call)
            }

            delete<TestMcpResource.NoAuth.Mcp> {
                transport?.close()
                transport = null
            }
        }

        return PrototypeToolServer.McpStreamableHttp(application.href(TestMcpResource.NoAuth.Mcp()))
    }

    fun asPrototypeToolServerParamAuth(application: Application): PrototypeToolServer {
        val authToken = UUID.randomUUID().toString()

        var transport: StreamableHttpServerTransport? = null
        application.routing {
            post<TestMcpResource.QueryParamAuth.Mcp> {
                if (it.authToken != authToken) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid auth token")
                    return@post
                }

                transport = streamableHttpTransport()
                transport!!.handlePost(call)
            }

            get<TestMcpResource.QueryParamAuth.Mcp> {
                if (it.authToken != authToken) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid auth token")
                    return@get
                }

                transport?.sseStream(call)
            }

            delete<TestMcpResource.QueryParamAuth.Mcp> {
                if (it.authToken != authToken) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid auth token")
                    return@delete
                }

                transport?.close()
                transport = null
            }
        }

        val replacementIdentifier = UUID.randomUUID().toString()
        return PrototypeToolServer.McpStreamableHttp(
            application.href(TestMcpResource.QueryParamAuth.Mcp(authToken = replacementIdentifier)),
            auth = PrototypeToolServerAuth.UrlTransformation(
                listOf(
                    PrototypeUrlTransformation(
                        replacementIdentifier,
                        PrototypeString.Inline(authToken)
                    )
                )
            )
        )
    }

    fun asPrototypeToolServerBearerAuth(application: Application): PrototypeToolServer {
        val authToken = UUID.randomUUID().toString()

        var transport: StreamableHttpServerTransport? = null
        application.plugin(Authentication).apply {
            configure {
                bearer("testMcpBearerAuth") {
                    authenticate { credential ->
                        if (credential.token != authToken)
                            return@authenticate null
                    }
                }
            }
        }

        application.routing {
            authenticate("testMcpBearerAuth") {
                post<TestMcpResource.BearerAuth.Mcp> {
                    transport = streamableHttpTransport()
                    transport!!.handlePost(call)
                }

                get<TestMcpResource.BearerAuth.Mcp> {
                    transport?.sseStream(call)
                }

                delete<TestMcpResource.BearerAuth.Mcp> {
                    transport?.close()
                    transport = null
                }
            }
        }

        return PrototypeToolServer.McpStreamableHttp(
            application.href(TestMcpResource.BearerAuth.Mcp()),
            auth = PrototypeToolServerAuth.Bearer(PrototypeString.Inline(authToken))
        )
    }
}

suspend fun KoinComponent.runTestServerTest(
    modelProvider: PrototypeModelProvider,
    testMcpServer: TestMcpServer,
    prototypeToolServer: PrototypeToolServer
) {
    val localSessionManager by inject<LocalSessionManager>()
    val secret = UUID.randomUUID().toString()

    val (session, _) = localSessionManager.createSession(
        "test", AgentGraph(
            agents = mapOf(
                graphAgentPair("test") {
                    registryAgent {
                        runtime(
                            PrototypeRuntime(
                                modelProvider,
                                prompts = PrototypePrompts(
                                    loop = PrototypeLoopPrompt(
                                        initial = PrototypeLoopInitialPrompt(
                                            extra = PrototypeString.Inline(
                                                "Call the '${testMcpServer.toolName}' tool with this input: $secret"
                                            )
                                        )
                                    )
                                ),
                                toolServers = listOf(prototypeToolServer),
                                iterationCount = 5
                            )
                        )
                    }
                    provider = GraphAgentProvider.Local(RuntimeId.PROTOTYPE)
                },
            )
        )
    )

    session.launchAgents()

    shouldCompleteWithin(30.seconds) {
        select {
            session.sessionScope.launch {
                session.joinAgents()
            }.onJoin {
                throw AssertionError("Agent runtime exited before the agent called the test tool")
            }

            session.sessionScope.launch {
                val value = testMcpServer.toolChannel.receive()
                if (value != secret)
                    throw AssertionError("Tool was called with the incorrect value: \"$value\", should be \"$secret\"")
            }.onJoin { }
        }
    }

    session.sessionScope.cancel()
}