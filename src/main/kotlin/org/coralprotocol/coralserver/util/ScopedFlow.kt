package org.coralprotocol.coralserver.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ScopedFlow<T>(val coroutineScope: CoroutineScope = CoroutineScope(Job())) {
    private val internalFlow = MutableSharedFlow<T>(
        extraBufferCapacity = 4096, // config?
    )

    val flow = internalFlow.asSharedFlow()

    fun emit(event: T) {
        coroutineScope.launch {
            internalFlow.emit(event)
        }
    }

    /**
     * Collects events until [close] is called, or other cancellation
     */
    suspend fun collectUntilCanceled(block: suspend (T) -> Unit) {
        try {
            coroutineScope.launch {
                internalFlow.collect(block)
            }.join()
        } catch (_: CancellationException) {
            // expected on when close is called
        }
    }

    fun close() {
        coroutineScope.cancel()
    }
}