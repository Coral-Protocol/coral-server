package org.coralprotocol.coralserver.session

import org.coralprotocol.coralserver.payment.PaymentSessionId

typealias SessionId = String

abstract class Session {
    /**
     * Unique ID for this session, passed to agents
     */
    abstract val id: SessionId

    /**
     * Kill all the agents involved in this session / clean up payment stuff etc.
     */
    abstract suspend fun destroy(sessionCloseMode: SessionCloseMode = SessionCloseMode.CLEAN)

    /**
     * Optional payment session ID for this session, attached if there are paid agents involved.
     */
    open val paymentSessionId: PaymentSessionId? = null
}