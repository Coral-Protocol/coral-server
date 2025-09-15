package org.coralprotocol.coralserver.agent.runtime

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.agent.graph.server.GraphAgentServer
import org.coralprotocol.coralserver.session.remote.createRemoteSessionClient

class RemoteRuntime(
    private val server: GraphAgentServer,
    private val claimId: String,
) : Orchestrate {
    override fun spawn(
        params: RuntimeParams,
        eventBus: EventBus<RuntimeEvent>,
        applicationRuntimeContext: ApplicationRuntimeContext
    ): OrchestratorHandle {
        if (params !is RuntimeParams.Local)
            throw IllegalArgumentException("A remote runtime must be given local runtime params")

        val webSocketClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
        }

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            webSocketClient.webSocket(
                host = server.address,
                port = server.port.toInt(),
                path = "/ws/v1/exported/$claimId",
            ) {
                createRemoteSessionClient(params.session, params.agentName)
            }
        }

        return object : OrchestratorHandle {
            override suspend fun destroy() {
                scope.cancel()
            }
        }
    }
}