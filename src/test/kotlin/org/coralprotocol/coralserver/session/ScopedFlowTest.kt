package org.coralprotocol.coralserver.session

import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.util.ScopedFlow
import kotlin.time.Duration.Companion.seconds

class ScopedFlowTest : CoralTest({
    test("testMultipleListeners").config(timeout = 30.seconds) {
        val maxListeners = 10
        val maxMessages = 100

        val scopedFlow = ScopedFlow<Int>()
        val totalCollected = MutableStateFlow(0)

        repeat(maxListeners) { _ ->
            launch {
                var collectCount = MutableStateFlow(0)

                scopedFlow.collectUntilCanceled { _ ->
                    collectCount.update { it + 1 }
                    totalCollected.update { it + 1 }
                }

                collectCount.value.shouldBeEqual(maxMessages)
            }
        }

        scopedFlow.subscriptionCount.first { it == maxListeners }

        repeat(maxMessages) {
            scopedFlow.emit(it)
        }

        totalCollected.first {
            it == maxListeners * maxMessages
        }

        scopedFlow.close()
    }

    test("testFailingListener").config(timeout = 30.seconds) {
        val scopedFlow = ScopedFlow<Int>()
        val collectorCount = MutableStateFlow(0)
        val receivedCount = MutableStateFlow(0)
        val messageCount = 1000
        launch {
            collectorCount.update { it + 1 }
            scopedFlow.collectUntilCanceled {
                receivedCount.update { it + 1 }
            }
        }

        launch {
            collectorCount.update { it + 1 }
            scopedFlow.collectUntilCanceled {
                cancel()
            }
        }

        collectorCount.first { it == 2 }
        repeat(messageCount) {
            scopedFlow.emit(1)
        }

        receivedCount.first { it == messageCount }
        scopedFlow.close()
    }
})