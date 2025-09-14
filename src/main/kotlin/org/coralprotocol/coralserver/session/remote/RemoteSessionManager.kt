package org.coralprotocol.coralserver.session.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.payment.PaymentSessionId
import org.coralprotocol.coralserver.payment.exporting.AggregatedPaymentClaimManager
import org.coralprotocol.coralserver.session.SessionCloseMode
import org.jetbrains.annotations.VisibleForTesting
import java.util.*

@VisibleForTesting
data class Claim(
    val id: String = UUID.randomUUID().toString(),
    val agent: GraphAgent,
    val maxCost: Long,
    val paymentSessionId: PaymentSessionId
)

class RemoteSessionManager(
    val orchestrator: Orchestrator,
    private val aggregatedPaymentClaimManager: AggregatedPaymentClaimManager
){
    @VisibleForTesting
    val claims = mutableMapOf<String, Claim>()
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
    fun createClaimNoPaymentCheck(
        agent: GraphAgent,
        paymentSessionId: PaymentSessionId,
        maxCost: Long
    ): String {
        paymentSessionCounts[paymentSessionId] = paymentSessionCounts.getOrDefault(paymentSessionId, 0u) + 1u

        val claim = Claim(
            agent = agent,
            paymentSessionId = paymentSessionId,
            maxCost = maxCost
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
            deferredMcpTransport = CompletableDeferred(),
            maxCost = claim.maxCost
        )

        remoteSession.sessionClosedFlow.onEach {
            cleanupSession(remoteSession, it)
        }.launchIn(remoteSession.coroutineScope)

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
    private suspend fun cleanupSession(remoteSession: RemoteSession, sessionCloseMode: SessionCloseMode) {
        val paymentSessionId = remoteSession.paymentSessionId
        if (paymentSessionId != null) {
            val paymentSessionCount = paymentSessionCounts.getOrDefault(paymentSessionId, 0u)
            if (paymentSessionCount == 1u) {
                aggregatedPaymentClaimManager.notifyPaymentSessionCosed(paymentSessionId)
                paymentSessionCounts.remove(paymentSessionId)
            }
            else {
                paymentSessionCounts[paymentSessionId] = paymentSessionCount - 1u
            }
        }

        // Remove the session after the session has been destroyed in case any cleanup requires a sessionId to session
        // lookup
        sessions.remove(remoteSession.id)
    }
}