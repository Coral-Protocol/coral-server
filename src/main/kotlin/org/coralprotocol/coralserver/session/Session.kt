package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.plus
import org.coralprotocol.coralserver.payment.PaymentSessionId

typealias SessionId = String

abstract class Session(parentScope: CoroutineScope) {
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
    val sessionScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

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