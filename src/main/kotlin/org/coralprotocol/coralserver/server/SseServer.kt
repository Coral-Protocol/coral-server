package org.coralprotocol.coralserver.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.collections.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.routes.messageRoutes
import org.coralprotocol.coralserver.routes.sessionRoutes
import org.coralprotocol.coralserver.routes.sseRoutes

private val logger = KotlinLogging.logger {}

/**
 * Runs an SSE MCP server with a plain configuration.
 * 
 * @param port The port to run the server on
 */
fun runSseMcpServerWithPlainConfiguration(port: Int): Unit = runBlocking {
    val mcpServersByTransportId = ConcurrentMap<String, Server>()

    // Load application configuration
    val appConfig = AppConfigLoader.loadConfig()
    logger.info { "Starting sse server on port $port with ${appConfig.applications.size} configured applications" }
    logger.info { "Use inspector to connect to the http://localhost:$port/{applicationId}/{privacyKey}/{sessionId}/sse" }

    embeddedServer(CIO, host = "0.0.0.0", port = port, watchPaths = listOf("classes")) {
        install(SSE)

        routing {
            // Configure all routes
            sessionRoutes()
            sseRoutes(mcpServersByTransportId)
            messageRoutes(mcpServersByTransportId)
        }
    }.start(wait = true)
}