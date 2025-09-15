package org.coralprotocol.coralserver.utils

import io.ktor.server.application.ServerReady
import io.mockk.spyk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.config.PaymentConfig
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.payment.blockchain.BlockchainService

class TestCoralServer(
    val host: String = "127.0.0.1",
    val port: UShort = 5555u,
    val devmode: Boolean = false,
    val blockchainServiceOverride: BlockchainService? = null
) {
    var server: CoralServer? = null

    @OptIn(DelicateCoroutinesApi::class)
    val serverContext = newFixedThreadPoolContext(5, "InlineTestCoralServer")

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun setup() {
        server?.stop()
        val config = Config(
            networkConfig = NetworkConfig(bindAddress = host, bindPort = port),
            paymentConfig = PaymentConfig()
        )
//        val blockchainService: BlockchainService = blockchainServiceOverride ?: createBlockchainService(config)
        val registry = AgentRegistry(agents = mutableListOf())
        val orchestrator: Orchestrator = spyk(Orchestrator(config, registry))

        server = CoralServer(
            devmode = devmode,
            config = config,
            registry = registry,
            blockchainService = blockchainServiceOverride,
            orchestrator = orchestrator
        )
        GlobalScope.launch(serverContext) {
            server!!.start()
        }
//        delay(700) // Give the server a moment to start
        val started = CompletableDeferred<Unit>()
        server!!.monitor.subscribe(ServerReady) {
//            logger.info { "Server started on $host:$port" }
            started.complete(Unit)
        }        // TODO: Poll for readiness
        started.await()
        // TODO: Use test http clients
    }

    fun getSessionManager() = server!!.localSessionManager
}