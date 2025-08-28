package org.coralprotocol.coralserver.e2e

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import org.coralprotocol.coralserver.config.ConfigCollection
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.SessionManager

class TestCoralServer(
    val host: String = "0.0.0.0",
    val port: UShort = 5555u,
    val devmode: Boolean = false,
    val sessionManager: SessionManager = SessionManager(port = port),
) {
    var server: CoralServer? = null

    @OptIn(DelicateCoroutinesApi::class)
    val serverContext = newFixedThreadPoolContext(1, "InlineTestCoralServer")

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun setup() {
        server?.stop()
        server = CoralServer(
            host = host,
            port = port,
            devmode = devmode,
            sessionManager = sessionManager,
            appConfig = ConfigCollection(null)
        )
        GlobalScope.launch(serverContext) {
            server!!.start()
        }
        delay(700) // Give the server a moment to start
        // TODO: Poll for readiness
        // TODO: Use test http clients
    }
}

