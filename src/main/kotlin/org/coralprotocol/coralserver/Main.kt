package org.coralprotocol.coralserver

//import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.agent.registry.LocalAgentRegistry
import org.coralprotocol.coralserver.config.BlockchainServiceProvider
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.loadFromFile
import org.coralprotocol.coralserver.server.CoralServer

private val logger = KotlinLogging.logger {}

// Reference to resources in main
class Main

/**
 * Start sse-server mcp on port 5555.
 *
 * @param args
 * - "--sse-server": Runs an SSE MCP server with a plain configuration.
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "--sse-server"
    val devMode = args.contains("--dev")
    val config = Config.loadFromFile()

    when (command) {
        "--sse-server" -> {
            val blockchainServiceProvider = BlockchainServiceProvider(config.paymentConfig)
            val registry = LocalAgentRegistry.loadFromFile(config)

            val server = CoralServer(
                devmode = devMode,
                config = config,
                registry = registry,
                blockchainService = blockchainServiceProvider.blockchainService,
                x402Service = blockchainServiceProvider.x402Service
            )

            // Add a shutdown hook to stop the server gracefully
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
