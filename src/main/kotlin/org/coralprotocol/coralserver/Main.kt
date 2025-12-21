package org.coralprotocol.coralserver

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.agent.debug.EchoDebugAgent
import org.coralprotocol.coralserver.agent.debug.PuppetDebugAgent
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.debug.ToolDebugAgent
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.config.BlockchainServiceProvider
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.loadFromFile
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.server.apiJsonConfig
import java.nio.file.Path

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
    val config = Config.loadFromFile()

    when (command) {
        "--sse-server" -> {
            val blockchainServiceProvider = BlockchainServiceProvider(config.paymentConfig)

            val regConfig = config.registryConfig
            val registry = AgentRegistry(config) {
                if (regConfig.enableMarketplaceAgentRegistrySource)
                    addMarketplace()

                regConfig.localRegistries.forEach { addLocal(Path.of(it)) }

                if (regConfig.includeDebugAgents) {
                    val client = HttpClient {
                        install(Resources)
                        install(SSE)
                        install(ContentNegotiation) {
                            json(apiJsonConfig, contentType = ContentType.Application.Json)
                        }
                        defaultRequest {
                            contentType(ContentType.Application.Json)
                            host = config.networkConfig.bindAddress
                            port = config.networkConfig.bindPort.toInt()
                        }
                    }

                    addLocalAgents(
                        listOf(
                            EchoDebugAgent(client).generate(),
                            SeedDebugAgent(client).generate(),
                            ToolDebugAgent(client).generate(),
                            PuppetDebugAgent(client).generate()
                        ),
                        "debug agents"
                    )
                }
            }

            runBlocking {
                val exported = registry.getExportedAgents()
                logger.info { "Registry contains ${registry.agents.size} agents and ${exported.size} exported agents" }
            }

            val server = CoralServer(
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
