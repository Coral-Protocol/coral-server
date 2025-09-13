package org.coralprotocol.coralserver

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.PaymentConfig
import org.coralprotocol.coralserver.config.Wallet
import org.coralprotocol.coralserver.config.WalletKeyType
import org.coralprotocol.coralserver.config.loadFromFile
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.BlockchainServiceImpl
import org.coralprotocol.payment.blockchain.models.SignerConfig
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.nio.file.Path
import java.util.Base64

private val logger = KotlinLogging.logger {}

// Reference to resources in main
class Main

/**
 * Start sse-server mcp on port 5555.
 *
 * @param args
 * - "--stdio": Runs an MCP server using standard input/output.
 * - "--sse-server": Runs an SSE MCP server with a plain configuration.
 * - "--dev": Runs the server in development mode.
 */
fun main(args: Array<String>) {
//    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE");
//    System.setProperty("io.ktor.development", "true")

    val command = args.firstOrNull() ?: "--sse-server"
    val devMode = args.contains("--dev")

    when (command) {
//        "--stdio" -> runMcpServerUsingStdio()
        "--sse-server" -> {
            val config = Config.loadFromFile()
            val registry = AgentRegistry.loadFromFile(config)
            val blockchainService = runBlocking { createBlockchainService(config) }

            val orchestrator = Orchestrator(config, registry, blockchainService)
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
        else -> {
            logger.error { "Unknown command: $command" }
        }
    }
}

@VisibleForTesting
suspend fun createBlockchainService(config: Config): BlockchainServiceImpl {
    return when (val wallet = config.paymentConfig.wallet) {
        is Wallet.Crossmint -> {
            val rpcUrl = config.paymentConfig.rpcUrl
            val apiKey = File(wallet.apiKeyPath).readText()

            // dummy config
            val signerConfig = SignerConfig.Crossmint(
                apiKey = apiKey,
                walletLocator = "",
                walletAddress = "",
                adminSignerLocator = "dummy",
                useStaging = true,
                deviceKeypairPath = wallet.keypairPath
            )

            val blockchainService = BlockchainServiceImpl(rpcUrl, "confirmed", signerConfig)
            val info = blockchainService.getCrossmintDelegatedKeypair(wallet.keypairPath, false).getOrThrow()

            if (info.createdNew) {
                println("go sign this key: " + info.publicKey) // todo: url
            }

            blockchainService
        }
    }
}