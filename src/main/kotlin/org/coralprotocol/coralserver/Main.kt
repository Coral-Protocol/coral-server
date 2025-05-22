package org.coralprotocol.coralserver

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.CoralServer

private val logger = KotlinLogging.logger {}

/**
 * Start sse-server mcp on port 5555.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 * - "--dev": Runs the server in development mode.
 */
fun main(args: Array<String>) {
//    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
//    System.setProperty("io.ktor.development", "true")

    val command = args.firstOrNull() ?: "--sse-server"
    val port = args.getOrNull(1)?.toUShortOrNull() ?: 5555u
    val devMode = args.contains("--dev")

    when (command) {
//        "--stdio" -> runMcpServerUsingStdio()
        "--sse-server" -> {
            val appConfig = AppConfigLoader.loadConfig()

            val orchestrator = Orchestrator(AgentRegistry(appConfig.registry ?: mapOf()))
            val server = CoralServer(
                port = port,
                devmode = devMode,
                appConfig = appConfig,
                sessionManager = SessionManager(orchestrator)
            )

            // Add shutdown hook to stop the server gracefully
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info { "Shutting down server..." }
                server.stop()
            })

            server.start(wait = true)
        }
        else -> {
            logger.error { "Unknown command: $command" }
        }
    }
}
