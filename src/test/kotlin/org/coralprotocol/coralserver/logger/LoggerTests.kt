package org.coralprotocol.coralserver.logger

import io.ktor.client.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.logging.LoggingEvent
import org.coralprotocol.coralserver.logging.LoggingTag
import org.coralprotocol.coralserver.modules.WEBSOCKET_COROUTINE_SCOPE_NAME
import org.coralprotocol.coralserver.routes.ws.v1.Logs
import org.coralprotocol.coralserver.util.filterIsInstance
import org.coralprotocol.coralserver.util.fromWsFrame
import org.coralprotocol.coralserver.util.map
import org.coralprotocol.coralserver.utils.TestEvent
import org.coralprotocol.coralserver.utils.shouldPostEventsFromBody
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.util.*
import kotlin.time.Duration.Companion.seconds

class LoggerTests : CoralTest({

    suspend fun CoroutineScope.genericLoggingTest(
        logs: Logs = Logs(),
        events: MutableList<TestEvent<LoggingEvent>>,
        block: suspend Logger.() -> Unit
    ) {
        val logger by inject<Logger>()
        val client by inject<HttpClient>()
        val json by inject<Json>()
        val websocketCoroutineScope by inject<CoroutineScope>(named(WEBSOCKET_COROUTINE_SCOPE_NAME))

        val wsJob = shouldPostEventsFromBody(
            timeout = 3.seconds,
            events = events,
        ) { flow ->
            val connection = CompletableDeferred<Unit>()
            val wsJob = launch {
                client.webSocket(client.href(Logs.WithToken(parent = logs, token = authToken))) {
                    connection.complete(Unit)
                    incoming
                        .filterIsInstance<Frame.Text>(this@webSocket)
                        .map(this@webSocket) {
                            it.fromWsFrame<LoggingEvent>(json)
                        }
                        .consumeEach {
                            flow.emit(it)
                        }
                }
            }

            connection.await()
            logger.block()

            wsJob
        }

        websocketCoroutineScope.cancel()
        wsJob.join()
    }

    test("testLoggingLevels") {
        genericLoggingTest(
            logs = Logs(
                allowSensitive = false,
                limit = 20
            ),
            events = mutableListOf(
                TestEvent("info") { it is LoggingEvent.Info },
                TestEvent("warn") { it is LoggingEvent.Warning },
                TestEvent("error") { it is LoggingEvent.Error }
            ),
        ) {
            info { "test" }
            warn { "test" }
            error { "test" }
        }
    }

    test("testLoggingReplay") {
        val logger by inject<Logger>()
        val events = mutableListOf<TestEvent<LoggingEvent>>()

        // first 10 messages should be dropped
        repeat(10) {
            logger.error { "this should not be included! $it" }
        }

        val limit = 50
        repeat(limit) {
            val randomId = UUID.randomUUID().toString()
            events.add(TestEvent("info message: $randomId") { it is LoggingEvent.Info && it.text == randomId })
            logger.info { randomId }
        }

        // this should include both prints that occurred before the description because of the limit 100 replay
        genericLoggingTest(
            logs = Logs(
                limit = limit
            ),
            events = (mutableListOf<TestEvent<LoggingEvent>>(
                TestEvent("warn") { it is LoggingEvent.Warning },
            ) + events).toMutableList(),
        ) {
            warn { "test" }
        }
    }

    test("testLoggingNoReplay") {
        val logger by inject<Logger>()
        logger.info { "info" }
        logger.error { "error" }

        // limit zero should filter previous buffer
        genericLoggingTest(
            logs = Logs(
                limit = 0
            ),
            events = mutableListOf(
                TestEvent("warn") { it is LoggingEvent.Warning },
            ),
        ) {
            warn { "test" }
        }
    }

    test("testFilterSensitive") {
        val logger by inject<Logger>()
        val sensitiveLogger = logger.withTags(LoggingTag.Sensitive)
        sensitiveLogger.info { "info" }
        sensitiveLogger.error { "error" }

        // limit zero should filter previous buffer
        genericLoggingTest(
            logs = Logs(
                limit = 100
            ),
            events = mutableListOf(
                TestEvent("warn") { it is LoggingEvent.Warning },
            ),
        ) {
            warn { "test" }
        }
    }

    test("testIncludeSensitive") {
        val logger by inject<Logger>()
        val sensitiveLogger = logger.withTags(LoggingTag.Sensitive)
        sensitiveLogger.info { "info" }
        sensitiveLogger.error { "error" }

        // limit zero should filter previous buffer
        genericLoggingTest(
            logs = Logs(
                allowSensitive = true
            ),
            events = mutableListOf(
                TestEvent("info") { it is LoggingEvent.Info },
                TestEvent("warn") { it is LoggingEvent.Warning },
                TestEvent("error") { it is LoggingEvent.Error },
            ),
        ) {
            warn { "test" }
        }
    }

    test("testAgentFilter") {
        val namespace = "default"
        val sessionId = UUID.randomUUID().toString()
        val agentName1 = "agent1"
        val agentName2 = "agent2"

        // limit zero should filter previous buffer
        genericLoggingTest(
            logs = Logs(
                agentFilter = agentName1,
                namespaceFilter = namespace,
                sessionFilter = sessionId,
                allowSensitive = true
            ),
            events = mutableListOf(
                TestEvent("info") { it is LoggingEvent.Info },
                TestEvent("error") { it is LoggingEvent.Error },
            ),
        ) {
            val agentLogger =
                withTags(LoggingTag.Namespace(namespace), LoggingTag.Session(sessionId), LoggingTag.Agent(agentName1))

            val agentLogger2 =
                withTags(LoggingTag.Namespace(namespace), LoggingTag.Session(sessionId), LoggingTag.Agent(agentName2))

            // neither of these should be caught because of the filters applied
            warn { "test" }
            agentLogger2.warn { "test" }

            agentLogger.info { "info" }
            agentLogger.error { "info" }
        }
    }

    test("testBufferSize") {
        val logger by inject<Logger>()
        logger.info { "should be dropped" }

        val events = mutableListOf<TestEvent<LoggingEvent>>()
        repeat(logBufferSize) {
            val randomId = UUID.randomUUID().toString()
            events.add(TestEvent("within buffer size $randomId") { it is LoggingEvent.Warning && it.text == randomId })
            logger.warn { randomId }
        }

        // limit zero should filter previous buffer
        genericLoggingTest(
            logs = Logs(
                limit = 2048 // should NOT include the first info message because it is outside the log's buffer size
            ),
            events = (events + mutableListOf(
                TestEvent("first error") { it is LoggingEvent.Error && it.text == "error1" },
                TestEvent("second error") { it is LoggingEvent.Error && it.text == "error2" },
            )).toMutableList(),
        ) {
            logger.error { "error1" }
            logger.error { "error2" }
        }
    }
})