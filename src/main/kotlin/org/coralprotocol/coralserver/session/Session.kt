package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.coralprotocol.coralserver.payment.PaymentSessionId

typealias SessionId = String

abstract class Session {
    /**
     * Unique ID for this session, passed to agents
     */
    abstract val id: SessionId

    /**
     * Optional payment session ID for this session, attached if there are paid agents involved.
     */
    open val paymentSessionId: PaymentSessionId? = null

    /**
     * Coroutine scope for this session
     */
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Called by the destroy function.  Should be listened to by managers to clean up any related context
     */
    val sessionClosedFlow: MutableSharedFlow<SessionCloseMode> = MutableSharedFlow()

    /**
     * Kill all the agents involved in this session / clean up payment stuff etc.
     */
    open suspend fun destroy(sessionCloseMode: SessionCloseMode = SessionCloseMode.CLEAN) {
        sessionClosedFlow.emit(sessionCloseMode)
    }
}