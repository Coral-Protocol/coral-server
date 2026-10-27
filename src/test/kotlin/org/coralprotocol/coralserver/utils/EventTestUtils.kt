package org.coralprotocol.coralserver.utils

import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.concurrent.suspension.shouldCompleteWithin
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.session.LocalSession
import kotlin.time.Duration

data class TestEvent<Event>(
    val description: String,
    val predicate: (event: Event) -> Boolean,
)

/**
 * Runs [block] and suspends until an event of type [Event] is posted to [eventFlow], failing if the event is not
 * posted within [timeout].  Only events posted after this function is called are considered.
 */
suspend inline fun <reified Event> shouldPostEvent(
    timeout: Duration,
    eventFlow: Flow<*>,
    crossinline block: suspend () -> Unit,
): Event = coroutineScope {
    // started undispatched so that the flow is subscribed to before block runs, otherwise events posted by block
    // before this coroutine was dispatched would be missed
    val event = async(start = CoroutineStart.UNDISPATCHED) {
        eventFlow.filterIsInstance<Event>().first()
    }

    try {
        { "expected event was not posted" }.asClue {
            shouldCompleteWithin(timeout) {
                block()
                event.await()
            }
        }
    } finally {
        event.cancel()
    }
}

/**
 * Runs [block] and suspends until every event in [events] has been matched by an event posted to [eventFlow], failing
 * if any are missing after [timeout].  Only events posted after this function is called are considered.  Matched
 * events are removed from [events].
 *
 * @param allowUnexpectedEvents If false, any posted event that does not match an event in [events] fails the test.
 */
suspend fun <Event, FlowType, R> shouldPostEvents(
    timeout: Duration,
    allowUnexpectedEvents: Boolean = false,
    events: MutableList<TestEvent<Event>> = mutableListOf(),
    eventFlow: FlowType,
    block: suspend (FlowType) -> R,
): R where FlowType : Flow<Event> = coroutineScope {
    var unexpectedEvent: Event? = null
    val expectingEvents = events.isNotEmpty()

    // started undispatched so that the flow is subscribed to before block runs, otherwise events posted by block
    // before this coroutine was dispatched would be missed
    val eventJob = launch(start = CoroutineStart.UNDISPATCHED) {
        eventFlow.takeWhile { event ->
            if (!events.removeAll { it.predicate(event) } && !allowUnexpectedEvents)
                unexpectedEvent = event

            unexpectedEvent == null && events.isNotEmpty()
        }.collect()
    }

    try {
        val result = withTimeoutOrNull(timeout) {
            val result = block(eventFlow)

            // with no expected events the collector only exists to catch unexpected events posted during block
            if (expectingEvents)
                eventJob.join()

            Result.success(result)
        }

        withClue("unexpected event: $unexpectedEvent") {
            unexpectedEvent shouldBe null
        }

        withClue("missing expected events: ${events.joinToString(", ") { it.description }}") {
            events.shouldBeEmpty()
            result.shouldNotBeNull()
        }.getOrThrow()
    } finally {
        eventJob.cancel()
    }
}

suspend fun <R> LocalSession.shouldPostEvents(
    timeout: Duration,
    allowUnexpectedEvents: Boolean = false,
    events: MutableList<TestEvent<SessionEvent>>,
    block: suspend (SharedFlow<SessionEvent>) -> R,
): R =
    shouldPostEvents(timeout, allowUnexpectedEvents, events, this@shouldPostEvents.events, block)

suspend inline fun <reified Event> LocalSession.shouldPostEvent(
    timeout: Duration,
    crossinline block: suspend () -> Unit,
) where Event : SessionEvent =
    shouldPostEvent<Event>(timeout, events, block)

suspend fun <Event, R> shouldPostEventsFromBody(
    timeout: Duration,
    allowUnexpectedEvents: Boolean = false,
    events: MutableList<TestEvent<Event>>,
    block: suspend (MutableSharedFlow<Event>) -> R,
): R {
    val flow = MutableSharedFlow<Event>()
    return shouldPostEvents(timeout, allowUnexpectedEvents, events, flow, block)
}

@Suppress("unused")
fun <Event> Iterable<Event>.shouldHaveEvents(events: MutableList<TestEvent<Event>>) {
    this.forEach { event ->
        events.removeAll { it.predicate(event) }
    }

    withClue("missing expected events: ${events.joinToString(", ") { it.description }}") {
        events.shouldBeEmpty()
    }
}
