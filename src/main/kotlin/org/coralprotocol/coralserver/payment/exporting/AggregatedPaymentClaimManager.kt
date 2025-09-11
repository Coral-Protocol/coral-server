package org.coralprotocol.coralserver.payment.exporting

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.coralprotocol.coralserver.payment.PaymentSessionId
import org.coralprotocol.coralserver.routes.api.v1.PaymentClaimRequest
import org.coralprotocol.coralserver.session.remote.RemoteSession

class AggregatedPaymentClaimManager() {
    private val claimMap = mutableMapOf<PaymentSessionId, MutableList<PaymentClaimRequest>>()
    private val remoteSessions = mutableMapOf<PaymentSessionId, MutableList<RemoteSession>>()
//    private val claimMap = mutableMapOf<String, MutableList<PaymentClaimRequest>>()
    private val submitClaimScope = CoroutineScope(Dispatchers.IO)

    /**
     * Called multiple times from one agent, probably called per "work" item
     */
    fun addClaim(claim: PaymentClaimRequest, session: RemoteSession) {
        val paymentSessionId =
            session.paymentSessionId ?: throw IllegalArgumentException("Payment session id cannot be null")

        claimMap.getOrPut(session.paymentSessionId) {
            mutableListOf()
        }.add(claim)

        remoteSessions.getOrPut(session.paymentSessionId) {
            mutableListOf()
        }
    }

    fun notifyPaymentSessionCosed(paymentSessionId: PaymentSessionId) {
        //
    }
}