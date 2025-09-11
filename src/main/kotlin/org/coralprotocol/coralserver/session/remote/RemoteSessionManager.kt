package org.coralprotocol.coralserver.session.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.payment.PaymentSessionId
import java.util.*
import kotlin.collections.set

private data class Claim(
    val id: String = UUID.randomUUID().toString(),
    val agent: GraphAgent,
    val paymentSessionId: PaymentSessionId
)

class RemoteSessionManager(
    val orchestrator: Orchestrator,
){
    private val scope = CoroutineScope(Dispatchers.IO)

    private val claims = mutableMapOf<String, Claim>()
    private val sessions = mutableMapOf<String, RemoteSession>()

    /**
     * This counts the number of sessions that use the same payment session.  When a session closes, we need to check if
     * it was the last session using the payment session, and if so, we can "claim" payment, ending the transaction
     *
     * It would be possible to do a similar thing with the [sessions] map, but, that the sessions there are only removed
     * when the session closes, and session closing can wait for the involved agents to exit - the latency involved
     * here is something we want to avoid with this map.
     */
    private val paymentSessionCounts: MutableMap<PaymentSessionId, UInt> = mutableMapOf()

    /**
     * Claims an agent that can later be executed by executeClaim
     */
    fun createClaimNoPaymentCheck(agent: GraphAgent, paymentSessionId: PaymentSessionId): String {
        paymentSessionCounts[paymentSessionId] = paymentSessionCounts.getOrDefault(paymentSessionId, 0u) + 1u

        val claim = Claim(
            agent = agent,
            paymentSessionId = paymentSessionId
        )
        claims[claim.id] = claim

        return claim.id
    }

    /**
     * Executes a claim and returns a remote session.
     */
    fun executeClaim(id: String): RemoteSession {
        val claim = claims[id] ?: throw IllegalArgumentException("Bad claim ID")
        val remoteSession = RemoteSession(
            id = id,
            agent = claim.agent,
            deferredMcpTransport = CompletableDeferred()
        )

        orchestrator.spawnRemote(
            session = remoteSession,
            graphAgent = claim.agent,
            agentName = claim.agent.name
        )

        sessions[id] = remoteSession
        return remoteSession
    }

    fun findSession(id: String): RemoteSession? = sessions[id]


    /**
     * Closes a session by ID
     */
    suspend fun closeSession(sessionId: String) {
        val session = sessions[sessionId]
            ?: throw IllegalArgumentException("invalid session id: $sessionId")

        val paymentSessionId = session.paymentSessionId
        if (paymentSessionId!= null) {
            val paymentSessionCount = paymentSessionCounts.getOrDefault(paymentSessionId, 0u)
            if (paymentSessionCount == 1u) {
                // todo: connect
                //AggregatedPaymentClaimManager.notifyPaymentSessionCosed(paymentSessionId)
                paymentSessionCounts.remove(paymentSessionId)
            }
            else {
                paymentSessionCounts[paymentSessionId] = paymentSessionCount - 1u
            }
        }

        session.destroy()

        // Remove the session after the session has been destroyed in case any cleanup requires a sessionId to session
        // lookup
        sessions.remove(sessionId)
    }
}