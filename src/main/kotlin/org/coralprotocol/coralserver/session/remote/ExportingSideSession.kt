package org.coralprotocol.coralserver.session.remote

import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.coroutines.CompletableDeferred
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.session.Session

/**
 * A Coral server can export agents to be used in another Coral server's sessions.  A remote session is the exporting
 * server's representation of the importing server's session.  It only contains the information required for agents
 * this server runs to communicate with the session from the importing server.
 *
 * This is a "serverside" class.  It is not used by the importing server.
 *
 * A remote session also represents a single agent, so we (the server, exporting the agent) can possibly have multiple
 * remote sessions that are associated with a single session from the importing server.
 */
class RemoteSession(
    /**
     * A unique ID for this remote session
     */
    override val id: String,

    /**
     * The agent that this session is providing
     */
    val agent: GraphAgent,

    /**
     * The transport between this server and the agent
     */
    val deferredMcpTransport: CompletableDeferred<SseServerTransport>
): Session {
    private val lifecycle = CompletableDeferred<Boolean>()

    suspend fun connectMcpTransport(transport: SseServerTransport): Boolean {
        deferredMcpTransport.complete(transport)
        return lifecycle.await()
    }

    fun close(status: Boolean) {
        lifecycle.complete(status)
    }
}