package org.coralprotocol.coralserver.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.Job
import org.coralprotocol.coralserver.config.AppConfig
import org.coralprotocol.coralserver.routes.messageRoutes
import org.coralprotocol.coralserver.routes.sessionRoutes
import org.coralprotocol.coralserver.routes.sseRoutes
import org.coralprotocol.coralserver.session.SessionManager

private val logger = KotlinLogging.logger {}

/**
 * CoralServer class that encapsulates the SSE MCP server functionality.
 *
 * @param host The host to run the server on
 * @param port The port to run the server on
 * @param devmode Whether the server is running in development mode
 */
class CoralServer(
    val host: String = "0.0.0.0",
    val port: UShort = 5555u,
    val appConfig: AppConfig,
    val devmode: Boolean = false,
    val sessionManager: SessionManager = SessionManager(port = port),
) {
    private val mcpServersByTransportId = ConcurrentMap<String, Server>()
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
        embeddedServer(CIO, host = host, port = port.toInt(), watchPaths = listOf("classes")) {
            install(SSE)
            install(ContentNegotiation) {
                json()
            }
            routing {
                // Configure all routes
                sessionRoutes(sessionManager, devmode)
                sseRoutes(mcpServersByTransportId, sessionManager)
                messageRoutes(mcpServersByTransportId, sessionManager)
            }
        }
    val monitor get() = server.monitor
    private var serverJob: Job? = null

    /**
     * Starts the server.
     */
    fun start(wait: Boolean = false) {
        logger.info { "Starting sse server on port $port with ${appConfig.applications.size} configured applications" }
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");

        if (devmode) {
            logger.info {
                "In development, agents can connect to " +
                        "http://localhost:$port/devmode/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=exampleAgent"
            }
            logger.info {
                "Connect the inspector to " +
                        "http://localhost:$port/devmode/exampleApplicationId/examplePrivacyKey/exampleSessionId/sse?agentId=inspector"
            }
        }
        server.monitor.subscribe(ApplicationStarted) {
            logger.info { "Server started on $host:$port" }
        }
        server.start(wait)
    }

    /**
     * Stops the server.
     */
    fun stop() {
        logger.info { "Stopping server..." }
        serverJob?.cancel()
        server.stop(1000, 2000)
        serverJob = null
        logger.info { "Server stopped" }
    }
}