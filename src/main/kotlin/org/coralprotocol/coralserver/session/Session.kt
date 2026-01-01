package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.coralprotocol.coralserver.payment.PaymentSessionId
import org.koin.core.component.KoinComponent

typealias SessionId = String

abstract class Session(parentScope: CoroutineScope, supervisedSessions: Boolean = true) : KoinComponent {
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
    } else {
        CoroutineScope(parentScope.coroutineContext + Job())
    }

    /**
     * True when the session is closing.  The session is considered closing as soon as all agents have completed their
     * lifecycles.  There can be a significant gap between closing and closed when session persistence settings keep
     * the session in memory.
     */
    var closing: Boolean = false
}