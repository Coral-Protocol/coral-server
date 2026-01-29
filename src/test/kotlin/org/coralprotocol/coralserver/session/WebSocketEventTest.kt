package org.coralprotocol.coralserver.session

import io.kotest.matchers.collections.shouldHaveSize
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.events.LocalSessionManagerEvent
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.modules.WEBSOCKET_COROUTINE_SCOPE_NAME
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.routes.ws.v1.Events
import org.coralprotocol.coralserver.util.filterIsInstance
import org.coralprotocol.coralserver.util.fromWsFrame
import org.coralprotocol.coralserver.util.map
import org.coralprotocol.coralserver.utils.TestEvent
import org.coralprotocol.coralserver.utils.dsl.sessionRequest
import org.coralprotocol.coralserver.utils.shouldHaveEvents
import org.coralprotocol.coralserver.utils.shouldPostEventsFromBody
import org.koin.core.qualifier.named
import org.koin.test.inject
import java.util.*
import kotlin.time.Duration.Companion.seconds

class WebSocketEventTest : CoralTest({
    test("testSessionEvents") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val json by inject<Json>()
        val websocketCoroutineScope by inject<CoroutineScope>(named(WEBSOCKET_COROUTINE_SCOPE_NAME))

        val threadCount = 10u
        val messageCount = 10u

        val id: SessionIdentifier = client.authenticatedPost(Sessions.Session()) {
            setBody(
                sessionRequest {
                    agentGraphRequest {
                        agent(SeedDebugAgent.identifier) {
                            provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)

                            option("START_DELAY", AgentOptionValue.UInt(100u))
                            option("OPERATION_DELAY", AgentOptionValue.UInt(1u))
                            option("SEED_THREAD_COUNT", AgentOptionValue.UInt(threadCount))
                            option("SEED_MESSAGE_COUNT", AgentOptionValue.UInt(messageCount))
                        }
                        isolateAllAgents()
                    }
                }
            )
        }.body()

        val eventsDeferred = CompletableDeferred<List<SessionEvent>>()
        launch {
            val url = client.href(
                Events.WithToken.SessionEvents(
                    Events.WithToken(token = authToken),
                    id.namespace,
                    id.sessionId
                )
            )

            client.webSocket(url) {
                eventsDeferred.complete(
                    incoming
                        .filterIsInstance<Frame.Text>(this@webSocket)
                        .map(this@webSocket) {
                            it.fromWsFrame<SessionEvent>(json)
                        }
                        .toList())
            }
        }

        localSessionManager.waitAllSessions()
        websocketCoroutineScope.cancel()

        val events = eventsDeferred.await()
        val threadEvents = events.filterIsInstance<SessionEvent.ThreadCreated>()
        val messageEvents = events.filterIsInstance<SessionEvent.ThreadMessageSent>()
        threadEvents.shouldHaveSize(threadCount.toInt())
        messageEvents.shouldHaveSize(threadCount.toInt() * messageCount.toInt())

        events.shouldHaveEvents(
            mutableListOf(
                TestEvent("runtime stopped") { it is SessionEvent.RuntimeStopped },
            )
        )
    }

    test("testLsmEvents") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val json by inject<Json>()
        val websocketCoroutineScope by inject<CoroutineScope>(named(WEBSOCKET_COROUTINE_SCOPE_NAME))

        val ns1Name = "ns1"
        val ns2Name = "ns2"

        val webSocketJob = this.shouldPostEventsFromBody(
            timeout = 3.seconds,
            events = mutableListOf(
                TestEvent("ns1 create") { it is LocalSessionManagerEvent.NamespaceCreated && it.namespace == ns1Name },
                TestEvent("ns1 session create") { it is LocalSessionManagerEvent.SessionCreated && it.namespace == ns1Name },
                TestEvent("ns1 session running") { it is LocalSessionManagerEvent.SessionRunning && it.namespace == ns1Name },
                TestEvent("ns1 destroy") { it is LocalSessionManagerEvent.NamespaceClosed && it.namespace == ns1Name },
                TestEvent("ns1 session closing") { it is LocalSessionManagerEvent.SessionClosing && it.namespace == ns1Name },
                TestEvent("ns1 session closed") { it is LocalSessionManagerEvent.SessionClosed && it.namespace == ns1Name },
            )
        ) { flow ->
            val wsJob = launch {
                val url = client.href(
                    Events.WithToken.LsmEvents(
                        Events.WithToken(token = authToken),
                        namespaceFilter = ns1Name
                    )
                )

                client.webSocket(url) {
                    incoming
                        .filterIsInstance<Frame.Text>(this@webSocket)
                        .map(this@webSocket) {
                            it.fromWsFrame<LocalSessionManagerEvent>(json)
                        }
                        .consumeEach {
                            flow.emit(it)
                        }
                }
            }

            // post sessions after WS connection established
            localSessionManager.events.subscriptionCount.first { it == 1 }

            for (namespaceName in listOf(ns1Name, ns2Name)) {
                client.authenticatedPost(Sessions.Session()) {
                    setBody(
                        sessionRequest {
                            createNamespaceIfNotExists {
                                name = namespaceName
                            }
                            agentGraphRequest {
                                agent(SeedDebugAgent.identifier) {
                                    provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)

                                    option("START_DELAY", AgentOptionValue.UInt(1000u))
                                    option("SEED_THREAD_COUNT", AgentOptionValue.UInt(1u))
                                    option("SEED_MESSAGE_COUNT", AgentOptionValue.UInt(1u))
                                }
                                isolateAllAgents()
                            }
                        }
                    )
                }
            }

            wsJob
        }

        webSocketJob.cancelAndJoin()
        localSessionManager.waitAllSessions()
        websocketCoroutineScope.cancel()
    }

    test("testLsmEventsWithAnnotationFilter") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val json by inject<Json>()
        val websocketCoroutineScope by inject<CoroutineScope>(named(WEBSOCKET_COROUTINE_SCOPE_NAME))


        val expectedEvents = mutableListOf<TestEvent<LocalSessionManagerEvent>>()
        val requests = mutableListOf<SessionRequest>()
        repeat(10) { index ->
            val filtered = index < 5
            val namespaceName = "namespace $index"
            requests.add(sessionRequest {
                createNamespaceIfNotExists {
                    name = namespaceName
                    if (filtered)
                        annotation("filtered", "true")
                }
                agentGraphRequest {
                    agent(SeedDebugAgent.identifier) {
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)

                        option("START_DELAY", AgentOptionValue.UInt(1000u))
                        option("SEED_THREAD_COUNT", AgentOptionValue.UInt(1u))
                        option("SEED_MESSAGE_COUNT", AgentOptionValue.UInt(1u))
                    }
                    isolateAllAgents()
                }
            })

            if (filtered) {
                expectedEvents.add(TestEvent("$namespaceName created") { it is LocalSessionManagerEvent.NamespaceCreated && it.namespace == namespaceName })
                expectedEvents.add(TestEvent("$namespaceName session created") { it is LocalSessionManagerEvent.SessionCreated && it.namespace == namespaceName })
                expectedEvents.add(TestEvent("$namespaceName session running") { it is LocalSessionManagerEvent.SessionRunning && it.namespace == namespaceName })
                expectedEvents.add(TestEvent("$namespaceName session closing") { it is LocalSessionManagerEvent.SessionClosing && it.namespace == namespaceName })
                expectedEvents.add(TestEvent("$namespaceName session closed") { it is LocalSessionManagerEvent.SessionClosed && it.namespace == namespaceName })
                expectedEvents.add(TestEvent("$namespaceName closing") { it is LocalSessionManagerEvent.NamespaceClosed && it.namespace == namespaceName })
            }
        }


        val webSocketJob = this.shouldPostEventsFromBody(
            timeout = 3.seconds,
            events = expectedEvents
        ) { flow ->
            val wsJob = launch {
                val url = client.href(
                    Events.WithToken.LsmEvents(
                        Events.WithToken(token = authToken),
                        namespaceAnnotationFilters = Base64.getUrlEncoder()
                            .encodeToString(json.encodeToString(mapOf("filtered" to "true")).toByteArray())
                    )
                )

                client.webSocket(url) {
                    incoming
                        .filterIsInstance<Frame.Text>(this@webSocket)
                        .map(this@webSocket) {
                            it.fromWsFrame<LocalSessionManagerEvent>(json)
                        }
                        .consumeEach {
                            flow.emit(it)
                        }
                }
            }

            // post sessions after WS connection established
            localSessionManager.events.subscriptionCount.first { it == 1 }

            requests.forEach {
                client.authenticatedPost(Sessions.Session()) {
                    setBody(it)
                }
            }

            wsJob
        }

        webSocketJob.cancelAndJoin()
        localSessionManager.waitAllSessions()
        websocketCoroutineScope.cancel()
    }
})