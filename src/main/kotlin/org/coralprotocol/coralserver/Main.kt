package org.coralprotocol.coralserver

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.server.runSseMcpServerWithPlainConfiguration

private val logger = KotlinLogging.logger {}

/**
 * Start sse-server mcp on port 3001.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server <port>": Runs an SSE MCP server with a plain configuration.
 */
fun main(args: Array<String>) {
//    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
//    System.setProperty("io.ktor.development", "true")

    val command = args.firstOrNull() ?: "--sse-server"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 3001
    when (command) {
//        "--stdio" -> runMcpServerUsingStdio()
        "--sse-server" -> runSseMcpServerWithPlainConfiguration(port)
        else -> {
            logger.error { "Unknown command: $command" }
        }
    }
}