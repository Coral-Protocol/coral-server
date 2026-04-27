package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.coralprotocol.coralserver.payment.PaymentSessionId
import org.koin.core.component.KoinComponent

typealias SessionId = String


abstract class Session(parentScope: CoroutineScope, supervisedSessions: Boolean = true) : KoinComponent,
    SessionResource {
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
    val sessionScope: CoroutineScope by lazy(LazyThreadSafetyMode.PUBLICATION) {
        CoroutineScope(
            parentScope.coroutineContext + if (supervisedSessions) {
                SupervisorJob(parentScope.coroutineContext[Job])
            } else {
                Job(parentScope.coroutineContext[Job])
            }
        )
    }

    var status: MutableStateFlow<SessionStatus> = MutableStateFlow(SessionStatus.PendingExecution)
}
