package org.coralprotocol.coralserver.utils

import org.coralprotocol.coralserver.session.MessageId
import org.coralprotocol.coralserver.session.SessionAgent

suspend fun SessionAgent.synchronizedMessageTransaction(sendMessageFn: suspend () -> MessageId) {
    val waiter = waiters.first { !it.deferred.isCompleted }

    val msgId = sendMessageFn()
    val returnedMsg = waiter.deferred.await()

    if (returnedMsg.id != msgId)
        throw IllegalStateException("$name's active waiter returned message ${returnedMsg.id} instead of expected $msgId")
}