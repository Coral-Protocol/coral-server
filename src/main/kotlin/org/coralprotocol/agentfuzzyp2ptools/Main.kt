package org.coralprotocol.agentfuzzyp2ptools

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.agentfuzzyp2ptools.tools.addThreadTools
import org.coralprotocol.agentfuzzyp2ptools.resources.MessageService

private val logger = KotlinLogging.logger {}
// Store MessageService instances for each server session
private val messageServices = ConcurrentMap<String, MessageService>()

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

    val command = args.firstOrNull() ?: "--sse-server-ktor"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 3001
    when (command) {
        "--stdio" -> runMcpServerUsingStdio()
        "--sse-server-ktor" -> runSseMcpServerUsingKtorPlugin(port)
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
    
    // Create and store MessageService for this server
    val messageService = MessageService(server)
    
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
    logger.info { "Starting sse server on port $port" }
    logger.info { "Use inspector to connect to the http://localhost:$port/sse" }

    embeddedServer(CIO, host = "0.0.0.0", port = port, watchPaths = listOf("classes")) {
        install(SSE)
        routing {
            sse("/sse") {
                val transport = SseServerTransport("/message", this)
                val server = configureServer()
                servers[transport.sessionId] = server
                val messageService = MessageService(server)
                messageServices[transport.sessionId] = messageService

                server.connect(transport)
            }
            post("/message") {
                logger.debug { "Received Message" }
                val sessionId: String = call.request.queryParameters["sessionId"]!!
                val transport = servers[sessionId]?.transport as? SseServerTransport
                if (transport == null) {
                    call.respond(HttpStatusCode.NotFound, "Session not found")
                    return@post
                }

                transport.handlePostMessage(call)
            }
        }
    }.start(wait = true)
}

/**
 * Starts an SSE (Server Sent Events) MCP server using the Ktor framework and the specified port.
 *
 * The url can be accessed in the MCP inspector at [http://localhost:$port]
 *
 * @param port The port number on which the SSE MCP server will listen for client connections.
 * @return Unit This method does not return a value.
 */
fun runSseMcpServerUsingKtorPlugin(port: Int): Unit = runBlocking {
    logger.info { "Starting sse server on port $port" }
    logger.info { "Use inspector to connect to the http://localhost:$port/sse" }

    embeddedServer(CIO, host = "0.0.0.0", port = port,) {
        mcp {
            configureServer()
        }
    }.start(wait = true)
}
