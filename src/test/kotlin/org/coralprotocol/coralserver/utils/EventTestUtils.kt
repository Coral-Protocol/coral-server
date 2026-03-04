package org.coralprotocol.coralserver.utils

import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.session.LocalSession
import kotlin.time.Duration

data class TestEvent<Event>(
    val description: String,
    val predicate: (event: Event) -> Boolean,
)

suspend fun <Event, FlowType, R> CoroutineScope.shouldPostEvents(
    timeout: Duration,
    allowUnexpectedEvents: Boolean = false,
    events: MutableList<TestEvent<Event>> = mutableListOf(),
    eventFlow: FlowType,
    block: suspend (FlowType) -> R,
): R where FlowType : Flow<Event> {
    val listening = CompletableDeferred<Unit>()
    val eventJob = launch {
        listening.complete(Unit)

        eventFlow.collect { event ->
            if (!events.removeAll { it.predicate(event) } && !allowUnexpectedEvents)
                throw AssertionError("Unexpected event: $event")

            if (events.isEmpty())
                cancel()
        }
    }

    val retVal = CompletableDeferred<R>()
    val blockJob = launch {
        listening.await()
        retVal.complete(block(eventFlow))
    };

    {
        "missing expected events: ${events.joinToString(", ") { it.description }}"
    }.asClue {
        withTimeoutOrNull(timeout) {
            joinAll(eventJob, blockJob)
        }.shouldNotBeNull()
    }

    return retVal.await()
}

suspend fun <R> LocalSession.shouldPostEvents(
    timeout: Duration,
    allowUnexpectedEvents: Boolean = false,
    events: MutableList<TestEvent<SessionEvent>>,
    block: suspend (SharedFlow<SessionEvent>) -> R,
): R =
    this.sessionScope.shouldPostEvents(timeout, allowUnexpectedEvents, events, this@shouldPostEvents.events, block)

suspend fun <Event, R> CoroutineScope.shouldPostEventsFromBody(
    timeout: Duration,
    allowUnexpectedEvents: Boolean = false,
    events: MutableList<TestEvent<Event>>,
    block: suspend (MutableSharedFlow<Event>) -> R,
): R {
    val flow = MutableSharedFlow<Event>()
    return shouldPostEvents(timeout, allowUnexpectedEvents, events, flow, block)
}

fun <Event> Iterable<Event>.shouldHaveEvents(events: MutableList<TestEvent<Event>>) {
    this.forEach { event ->
        events.removeAll { it.predicate(event) }
    }

    withClue("missing expected events: ${events.joinToString(", ") { it.description }}") {
        events.shouldBeEmpty()
    }
}