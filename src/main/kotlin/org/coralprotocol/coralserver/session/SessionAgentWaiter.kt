package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CompletableDeferred

class SessionAgentWaiter(val filters: Set<SessionThreadMessageFilter>){
    val deferred = CompletableDeferred<SessionThreadMessage>()

    fun matches(message: SessionThreadMessage) = filters.all { it.matches(message) }
}