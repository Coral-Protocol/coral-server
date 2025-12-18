@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.server

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Job
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.exporting.AggregatedPaymentClaimManager
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.X402Service

private val logger = KotlinLogging.logger {}

val apiJsonConfig = Json {
    encodeDefaults = true
    prettyPrint = true
    explicitNulls = false
}

/**
 * CoralServer class that encapsulates the SSE MCP server functionality.
 *
 * @param host The host to run the server on
 * @param port The port to run the server on
 * @param devmode Whether the server is running in development mode
 */
class CoralServer(
    val config: Config,
    val registry: AgentRegistry,
    val blockchainService: BlockchainService? = null,
    val x402Service: X402Service? = null,
    val launchMode: LaunchMode = LaunchMode.DEDICATED
) {
    val jupiterService = JupiterService()

    val localSessionManager = LocalSessionManager(
        blockchainService = blockchainService,
        applicationRuntimeContext = ApplicationRuntimeContext(config),
        jupiterService = jupiterService,
        config = config,
        httpClient = HttpClient(),
    )

    val aggregatedPaymentClaimManager = if (blockchainService != null) {
        AggregatedPaymentClaimManager(blockchainService, jupiterService)
    } else {
        null
    }

//    val remoteSessionManager = if (aggregatedPaymentClaimManager != null) {
//        RemoteSessionManager(null, aggregatedPaymentClaimManager)
//    }
//    else {
//        null
//    }

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
        embeddedServer(
            CIO,
            host = config.networkConfig.bindAddress,
            port = config.networkConfig.bindPort.toInt(),
            watchPaths = listOf("classes")
        ) {
            coralServerModule(
                config = config,
                localSessionManager = localSessionManager,
                registry = registry,
                x402Service = x402Service,
                blockchainService = blockchainService
            )
        }

    val monitor get() = server.monitor
    private var serverJob: Job? = null

    /**
     * Starts the server.
     */
    fun start(wait: Boolean = false) {
        logger.info { "Starting sse server on port ${config.networkConfig.bindPort}" }
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");

        server.start(wait)
        server.application.routing { }.getAllRoutes()
            .forEach { logger.info { it.toString() } }
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
