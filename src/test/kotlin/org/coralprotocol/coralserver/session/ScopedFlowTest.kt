package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.coralprotocol.coralserver.util.ScopedFlow
import kotlin.time.Duration.Companion.seconds

class ScopedFlowTest : FunSpec({
    test("testMultipleListeners").config(timeout = 5.seconds) {
        val maxListeners = 10
        val maxMessages = 100

        val scopedFlow = ScopedFlow<Int>()
        val listenerCount = MutableStateFlow(0)
        val totalCollected = MutableStateFlow(0)

        repeat(maxListeners) { _ ->
            launch {
                listenerCount.update { it + 1 }

                var collectCount = 0
                scopedFlow.collectUntilCanceled { _ ->
                    collectCount++
                    totalCollected.update { it + 1 }
                }
                collectCount.shouldBeEqual(maxMessages)
            }
        }

        listenerCount.first { it == maxListeners }

        repeat(maxMessages) {
            scopedFlow.emit(1)
        }

        totalCollected.first {
            it == maxListeners * maxMessages
        }
        scopedFlow.close()
    }

    test("testFailingListener") {
        val scopedFlow = ScopedFlow<Int>()
        val collectorCount = MutableStateFlow(0)
        val messageCount = 10
        launch {
            var count = 0
            collectorCount.update { it + 1 }
            scopedFlow.collectUntilCanceled {
                count++
            }

            count.shouldBe(messageCount)
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

        scopedFlow.close()
    }
})