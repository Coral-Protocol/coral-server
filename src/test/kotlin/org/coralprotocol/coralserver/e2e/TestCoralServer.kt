package org.coralprotocol.coralserver.e2e

import io.mockk.spyk
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.server.CoralServer

class TestCoralServer(
    val host: String = "127.0.0.1",
    val port: UShort = 5555u,
    val devmode: Boolean = false,

) {
    var server: CoralServer? = null

    @OptIn(DelicateCoroutinesApi::class)
    val serverContext = newFixedThreadPoolContext(1, "InlineTestCoralServer")

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun setup() {
        server?.stop()
        val config = Config(NetworkConfig(bindAddress = host, bindPort = port))
        val registry = AgentRegistry(mutableMapOf(), mutableMapOf())
        val orchestrator: Orchestrator = spyk(Orchestrator(config, registry))

        server = CoralServer(
            devmode = devmode,
            config = config,
            registry = registry,
            orchestrator = orchestrator
        )
        GlobalScope.launch(serverContext) {
            server!!.start()
        }
        delay(700) // Give the server a moment to start
        // TODO: Poll for readiness
        // TODO: Use test http clients
    }

    fun getSessionManager() = server!!.localSessionManager
}

