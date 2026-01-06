package org.coralprotocol.coralserver.session

import io.kotest.matchers.collections.shouldHaveSize
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.util.filterIsInstance
import org.coralprotocol.coralserver.util.fromWsFrame
import org.coralprotocol.coralserver.util.map
import org.coralprotocol.coralserver.utils.TestEvent
import org.coralprotocol.coralserver.utils.dsl.sessionRequest
import org.coralprotocol.coralserver.utils.shouldHaveEvents
import org.coralprotocol.coralserver.utils.shouldPostEventsFromBody
import org.koin.core.qualifier.named
import org.koin.test.inject
import kotlin.time.Duration.Companion.seconds

class WebSocketTest : CoralTest({
    test("testSessionEvents") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val json by inject<Json>()
        val websocketCoroutineScope by inject<CoroutineScope>(named(WEBSOCKET_COROUTINE_SCOPE_NAME))

        val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
        val threadCount = 10u
        val messageCount = 10u

        val id: SessionIdentifier = client.authenticatedPost(namespace) {
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

    test("testLocalSessionManagerEvents") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val json by inject<Json>()
        val websocketCoroutineScope by inject<CoroutineScope>(named(WEBSOCKET_COROUTINE_SCOPE_NAME))

        val ns1Name = "ns1"
        val ns1 = Sessions.WithNamespace(namespace = ns1Name)

        val ns2Name = "ns2"
        val ns2 = Sessions.WithNamespace(namespace = ns2Name)

        val webSocketJob = this.shouldPostEventsFromBody(
            timeout = 3.seconds,
            events = mutableListOf(
                TestEvent("ns1 create") { it is LocalSessionManagerEvent.NamespaceCreated && it.namespace == ns1Name },
                TestEvent("ns1 session create") { it is LocalSessionManagerEvent.SessionCreated && it.namespace == ns1Name },
                TestEvent("ns1 destroy") { it is LocalSessionManagerEvent.NamespaceClosed && it.namespace == ns1Name },
                TestEvent("ns1 session closing") { it is LocalSessionManagerEvent.SessionClosing && it.namespace == ns1Name },
                TestEvent("ns1 session closed") { it is LocalSessionManagerEvent.SessionClosed && it.namespace == ns1Name },
            )
        ) { flow ->
            val wsJob = launch {
                val url = client.href(
                    Events.WithToken.LsmEvents(
                        Events.WithToken(
                            parent = Events(namespaceFilter = ns1Name),
                            token = authToken
                        )
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

            for (ns in listOf(ns1, ns2)) {
                client.authenticatedPost(ns) {
                    setBody(
                        sessionRequest {
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

        localSessionManager.waitAllSessions()
        websocketCoroutineScope.cancel()
        webSocketJob.join()
    }
})

suspend inline fun <reified T> collectWsEvents(
    client: HttpClient,
    json: Json,
    url: String,
    scope: CoroutineScope
): CompletableDeferred<List<T>> {
    val eventsDeferred = CompletableDeferred<List<T>>()



    return eventsDeferred
}