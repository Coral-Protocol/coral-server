@file:OptIn(ExperimentalCoroutinesApi::class)

package org.coralprotocol.coralserver.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce

fun <T, R> ReceiveChannel<T>.map(
    scope: CoroutineScope,
    transform: (T) -> R
): ReceiveChannel<R> = scope.produce {
    for (item in this@map) {
        send(transform(item))
    }
}

inline fun <reified R> ReceiveChannel<*>.filterIsInstance(
    scope: CoroutineScope
): ReceiveChannel<R> = scope.produce {
    for (item in this@filterIsInstance) {
        if (item is R)
            send(item)
    }
}