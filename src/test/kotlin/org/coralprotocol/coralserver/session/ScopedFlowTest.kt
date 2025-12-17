package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.coralprotocol.coralserver.util.EventFlow
import kotlin.time.Duration.Companion.seconds

class EventFlowTest : FunSpec({
    test("testMultipleListeners").config(timeout = 5.seconds) {
        val maxListeners = 10
        val maxMessages = 100

        val eventFlow = EventFlow<Int>(this)
        val listenerCount = MutableStateFlow(0)
        val totalCollected = MutableStateFlow(0)

        repeat(maxListeners) { _ ->
            launch {
                listenerCount.update { it + 1 }

                var collectCount = 0
                eventFlow.collectUntilClosed { _ ->
                    collectCount++
                    totalCollected.update { it + 1 }
                }
                collectCount.shouldBeEqual(maxMessages)
            }
        }

        listenerCount.first { it == maxListeners }

        repeat(maxMessages) {
            eventFlow.emit(1)
        }

        totalCollected.first {
            it == maxListeners * maxMessages
        }
        eventFlow.close()
    }
})