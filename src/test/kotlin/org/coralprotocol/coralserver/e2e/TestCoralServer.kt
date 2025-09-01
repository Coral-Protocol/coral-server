package org.coralprotocol.coralserver.e2e

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.SessionManager

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
        val config = ConfigCollection(
            defaultConfig = Config(NetworkConfig(bindAddress = host, bindPort = port)),
            configPath = null,
            registryPath = null,
            defaultRegistry = AgentRegistry(
                importedAgents = mapOf(),
                exportedAgents = mapOf()
            )
        )
        val orchestrator: Orchestrator = Orchestrator(config)
        server = CoralServer(
            devmode = devmode,
//            sessionManager = sessionManager,
            appConfig = config,
            orchestrator = orchestrator
        )
        GlobalScope.launch(serverContext) {
            server!!.start()
        }
        delay(700) // Give the server a moment to start
        // TODO: Poll for readiness
        // TODO: Use test http clients
    }

    fun getSessionManager() = server!!.sessionManager
}

