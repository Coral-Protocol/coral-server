package org.coralprotocol.coralserver.session.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import java.util.UUID

class RemoteSessionManager(
    val orchestrator: Orchestrator
){
    private val scope = CoroutineScope(Dispatchers.IO)

    private val claims = mutableMapOf<String, GraphAgent>()
    private val sessions = mutableMapOf<String, RemoteSession>()

    /**
     * Claims an agent that can later be executed by executeClaim
     */
    fun createClaim(agent: GraphAgent): String {
        val id = UUID.randomUUID().toString()
        claims[id] = agent

        return id
    }

    /**
     * Executes a claim and returns a remote session.
     */
    fun executeClaim(id: String): RemoteSession {
        val agent = claims[id] ?: throw IllegalArgumentException("Bad claim ID")
        val remoteSession = RemoteSession(
            id = id,
            agent = agent,
            deferredMcpTransport = CompletableDeferred()
        )

//        orchestrator.spawnRemote(
//            remoteSessionId = id,
//            graphAgent = agent,
//            agentName = agent.name
//        )

        sessions[id] = remoteSession
        return remoteSession
    }

    fun findSession(id: String): RemoteSession? = sessions[id]
}