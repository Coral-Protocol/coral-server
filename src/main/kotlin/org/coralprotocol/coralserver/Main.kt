package org.coralprotocol.coralserver

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.loadFromFile
import org.coralprotocol.coralserver.payment.keygen.CrossmintInteractiveKeyGenerator
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.payment.blockchain.BlockchainService

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
//    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
//    System.setProperty("io.ktor.development", "true")

    val command = args.firstOrNull() ?: "--sse-server"
    val devMode = args.contains("--dev")
    val config = Config.loadFromFile()

    when (command) {
        "--sse-server" -> {
            val blockchainService = runBlocking {
                BlockchainService.loadFromFile(config)
            }

            val registry = AgentRegistry.loadFromFile(config)

            val orchestrator = Orchestrator(config, registry)
            val server = CoralServer(
                devmode = devMode,
                config = config,
                registry = registry,
                orchestrator = orchestrator,
                blockchainService = blockchainService
            )

            // Add a shutdown hook to stop the server gracefully
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info { "Shutting down server..." }
                server.stop()
                runBlocking {
                    orchestrator.destroy()
                }
            })

            server.start(wait = true)
        }

        "--interactive-keygen-crossmint" -> {
            runBlocking {
                CrossmintInteractiveKeyGenerator(config, config.paymentConfig.rpcUrl).start()
            }
        }

        else -> {
            logger.error { "Unknown command: $command" }
        }
    }
}