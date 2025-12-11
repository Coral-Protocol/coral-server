package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CompletableDeferred

class SessionAgentWaiter(val filters: Set<SessionThreadMessageFilter>){
    val deferred = CompletableDeferred<SessionThreadMessage>()

    fun tryMessage(message: SessionThreadMessage) {
        if (filters.all { it.matches(message) })
            deferred.complete(message)
    }
}