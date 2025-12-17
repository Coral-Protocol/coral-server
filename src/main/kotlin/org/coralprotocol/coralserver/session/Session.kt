package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.plus
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.payment.PaymentSessionId

typealias SessionId = String

abstract class Session(parentScope: CoroutineScope, supervisedSessions: Boolean = true) {
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
    val sessionScope = if (supervisedSessions) {
        CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    }
    else {
        CoroutineScope(parentScope.coroutineContext)
    }

    /**
     * Called by the above session manager when this session is closed
     */
    abstract fun close()
}