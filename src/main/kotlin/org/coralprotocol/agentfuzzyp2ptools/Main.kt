package org.coralprotocol.agentfuzzyp2ptools

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.Serializable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.agentfuzzyp2ptools.config.AppConfigLoader
import org.coralprotocol.agentfuzzyp2ptools.session.SessionManager
import org.coralprotocol.agentfuzzyp2ptools.tools.addThreadTools

private val logger = KotlinLogging.logger {}

/**
 * Data class for session creation request.
 */
@Serializable
data class CreateSessionRequest(
    val applicationId: String,
    val privacyKey: String
)

/**
 * Data class for session creation response.
 */
@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    val applicationId: String,
    val privacyKey: String
)

/**
 * Start sse-server mcp on port 3001.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server-ktor <port>": Runs an SSE MCP server using Ktor plugin (default if no argument is provided).
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 */
fun main(args: Array<String>) {
//    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
//    System.setProperty("io.ktor.development", "true")

    val command = args.firstOrNull() ?: "--sse-server"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 3001
    when (command) {
        "--stdio" -> runMcpServerUsingStdio()
        "--sse-server" -> runSseMcpServerWithPlainConfiguration(port)
        else -> {
            logger.error { "Unknown command: $command" }
        }
    }
}

fun configureServer(): Server {
    val server = Server(
        Implementation(
            name = "mcp-kotlin test server",
            version = "0.1.0"
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                prompts = ServerCapabilities.Prompts(listChanged = true),
                resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
                tools = ServerCapabilities.Tools(listChanged = true),
            )
        ),
    )

    // Add thread-based tools
    server.addThreadTools()

    return server
}

fun runMcpServerUsingStdio() {
    // Note: The server will handle listing prompts, tools, and resources automatically.
    // The handleListResourceTemplates will return empty as defined in the Server code.
    val server = configureServer()
    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered()
    )

    runBlocking {
        server.connect(transport)
        val done = Job()
        done.join()
        logger.info { "Server closed" }
    }
}


fun runSseMcpServerWithPlainConfiguration(port: Int): Unit = runBlocking {
    val servers = ConcurrentMap<String, Server>()

    // Load application configuration
    val appConfig = AppConfigLoader.loadConfig()
    logger.info { "Starting sse server on port $port with ${appConfig.applications.size} configured applications" }
    logger.info { "Use inspector to connect to the http://localhost:$port/{applicationId}/{privacyKey}/{sessionId}/sse" }

    embeddedServer(CIO, host = "0.0.0.0", port = port, watchPaths = listOf("classes")) {
        install(SSE)

        routing {
            // Session creation endpoint
            post("/sessions") {
                try {
                    val request = call.receive<CreateSessionRequest>()

                    // Validate application and privacy key
                    if (!AppConfigLoader.isValidApplication(request.applicationId, request.privacyKey)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid application ID or privacy key")
                        return@post
                    }

                    // Create a new session
                    val session = SessionManager.createSession(request.applicationId, request.privacyKey)

                    // Return the session details
                    call.respond(
                        CreateSessionResponse(
                            sessionId = session.id,
                            applicationId = session.applicationId,
                            privacyKey = session.privacyKey
                        )
                    )

                    logger.info { "Created new session ${session.id} for application ${session.applicationId}" }
                } catch (e: Exception) {
                    logger.error(e) { "Error creating session" }
                    call.respond(HttpStatusCode.InternalServerError, "Error creating session: ${e.message}")
                }
            }

            // DevMode endpoint - creates session if it doesn't exist and allows any application ID and privacy key
            sse("/devmode/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
                val applicationId = call.parameters["applicationId"]
                val privacyKey = call.parameters["privacyKey"]
                val sessionId = call.parameters["coralSessionId"]
                val waitForAgents = call.request.queryParameters["waitForAgents"]?.toIntOrNull() ?: 0

                if (applicationId == null || privacyKey == null || sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
                    return@sse
                }

                // Get or create the session (no validation in dev mode)
                val session = SessionManager.getOrCreateSession(sessionId, applicationId, privacyKey)

                // Set the waitForAgents parameter in the session
                if (waitForAgents > 0) {
                    session.waitForAgents = waitForAgents
                    logger.info { "DevMode: Setting waitForAgents=$waitForAgents for session $sessionId" }
                }

                // Create the transport and server
                val transport = SseServerTransport("/devmode/$applicationId/$privacyKey/$sessionId/message", this)
                val server = configureServer()

                // Add the server to the session
                session.servers.add(server)
                servers[transport.sessionId] = server

                // Connect the server to the transport
                server.connect(transport)

                logger.info { "DevMode: Connected to session $sessionId with application $applicationId (waitForAgents=$waitForAgents)" }
            }

            // SSE endpoint with application, privacy key, and session parameters
            sse("/{applicationId}/{privacyKey}/{coralSessionId}/sse") {
                val applicationId = call.parameters["applicationId"]
                val privacyKey = call.parameters["privacyKey"]
                val sessionId = call.parameters["coralSessionId"]

                if (applicationId == null || privacyKey == null || sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
                    return@sse
                }

                // Get the session
                val session = SessionManager.getSession(sessionId)
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@sse
                }

                // Validate that the application and privacy key match the session
                if (session.applicationId != applicationId || session.privacyKey != privacyKey) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid application ID or privacy key for this session")
                    return@sse
                }

                // Create the transport and server
                val transport = SseServerTransport("/$applicationId/$privacyKey/$sessionId/message", this)
                val server = configureServer()

                // Add the server to the session
                session.servers.add(server)
                servers[transport.sessionId] = server

                // Connect the server to the transport
                server.connect(transport)
            }

            // Message endpoint with application, privacy key, and session parameters
            post("/{applicationId}/{privacyKey}/{coralSessionId}/message") {
                logger.debug { "Received Message" }

                val applicationId = call.parameters["applicationId"]
                val privacyKey = call.parameters["privacyKey"]
                val sessionId = call.parameters["coralSessionId"]
                val transportSessionId = call.request.queryParameters["sessionId"]

                if (applicationId == null || privacyKey == null || sessionId == null || transportSessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
                    return@post
                }

                // Get the session
                val session = SessionManager.getSession(sessionId)
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@post
                }

                // Validate that the application and privacy key match the session
                if (session.applicationId != applicationId || session.privacyKey != privacyKey) {
                    call.respond(HttpStatusCode.Forbidden, "Invalid application ID or privacy key for this session")
                    return@post
                }

                // Get the transport
                val transport = servers[transportSessionId]?.transport as? SseServerTransport
                if (transport == null) {
                    call.respond(HttpStatusCode.NotFound, "Transport not found")
                    return@post
                }

                // Handle the message
                try {
                    transport.handlePostMessage(call)
                } catch (e: NoSuchElementException) {
                    logger.error(e) { "This error likely comes from an inspector or non-essential client and can probably be ignored. See https://github.com/modelcontextprotocol/kotlin-sdk/issues/7" }
                    call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
                }
            }

            // DevMode message endpoint - no validation
            post("/devmode/{applicationId}/{privacyKey}/{coralSessionId}/message") {
                logger.debug { "Received DevMode Message" }

                val applicationId = call.parameters["applicationId"]
                val privacyKey = call.parameters["privacyKey"]
                val sessionId = call.parameters["coralSessionId"]
                val transportSessionId = call.request.queryParameters["sessionId"]

                if (applicationId == null || privacyKey == null || sessionId == null || transportSessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing required parameters")
                    return@post
                }

                // Get the session. It should exist even in dev mode as it was created in the sse endpoint
                val session = SessionManager.getSession(sessionId)
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@post
                }

                // Get the transport
                val transport = servers[transportSessionId]?.transport as? SseServerTransport
                if (transport == null) {
                    call.respond(HttpStatusCode.NotFound, "Transport not found")
                    return@post
                }

                // Handle the message
                try {
                    transport.handlePostMessage(call)
                } catch (e: NoSuchElementException) {
                    logger.error(e) { "This error likely comes from an inspector or non-essential client and can probably be ignored. See https://github.com/modelcontextprotocol/kotlin-sdk/issues/7" }
                    call.respond(HttpStatusCode.InternalServerError, "Error handling message: ${e.message}")
                }
            }
        }
    }.start(wait = true)
}