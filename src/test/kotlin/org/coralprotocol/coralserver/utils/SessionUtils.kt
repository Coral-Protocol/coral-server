package org.coralprotocol.coralserver.utils

import kotlinx.coroutines.flow.first
import org.coralprotocol.coralserver.session.MessageId
import org.coralprotocol.coralserver.session.SessionAgent

suspend fun SessionAgent.synchronizedMessageTransaction(sendMessageFn: suspend () -> MessageId) {
    // waiters are removed from this list before they are completed
    val waiter = waiters.first { it.isNotEmpty() }.first()

    val msgId = sendMessageFn()
    val returnedMsg = waiter.deferred.await()

    if (returnedMsg.id != msgId)
        throw IllegalStateException("$name's active waiter returned message ${returnedMsg.id} instead of expected $msgId")
}