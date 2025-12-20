package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.launch
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.events.LocalSessionManagerEvent
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.routes.ws.v1.Events
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.session.models.SessionRequest
import org.coralprotocol.coralserver.util.filterIsInstance
import org.coralprotocol.coralserver.util.fromWsFrame
import org.coralprotocol.coralserver.util.map
import kotlin.time.Duration.Companion.seconds

class WebSocketTest : FunSpec({
    test("testSessionEvents") {
        sessionTest({
            addLocalAgents(
                listOf(
                    SeedDebugAgent(it.client).generate(),
                ), "debug agents"
            )
        }) {
            val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
            val threadCount = 10u
            val messageCount = 10u

            val id: SessionIdentifier = ktor.client.post(namespace) {
                withAuthToken()
                contentType(ContentType.Application.Json)
                setBody(
                    SessionRequest(
                        agentGraphRequest = AgentGraphRequest(
                            agents = listOf(
                                GraphAgentRequest(
                                    id = SeedDebugAgent.identifier,
                                    name = "seed",
                                    description = "",
                                    provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                    options = mapOf(
                                        "START_DELAY" to AgentOptionValue.UInt(100u),
                                        "OPERATION_DELAY" to AgentOptionValue.UInt(1u),
                                        "SEED_THREAD_COUNT" to AgentOptionValue.UInt(threadCount),
                                        "SEED_MESSAGE_COUNT" to AgentOptionValue.UInt(messageCount),
                                    )
                                )
                            ),
                            groups = setOf(setOf("seed")),
                        )
                    )
                )
            }.body()

            val eventsDeferred = collectWsEvents<SessionEvent>(
                ktor.client.href(
                    Events.WithToken.SessionEvents(
                        Events.WithToken(token = authToken),
                        id.namespace,
                        id.sessionId
                    )
                ),
                this@test
            )

            sessionManager.waitAllSessions()

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
    }

    test("testLocalSessionManagerEvents") {
        sessionTest({
            addLocalAgents(
                listOf(
                    SeedDebugAgent(it.client).generate(),
                ), "debug agents"
            )
        }) {
            val ns1Name = "ns1"
            val ns1 = Sessions.WithNamespace(namespace = ns1Name)

            val ns2Name = "ns2"
            val ns2 = Sessions.WithNamespace(namespace = ns2Name)

            val webSocketJob = shouldPostEventsFromBody(
                timeout = 3.seconds,
                events = mutableListOf(
                    TestEvent("ns1 create") { it is LocalSessionManagerEvent.NamespaceCreated && it.namespace == ns1Name },
                    TestEvent("ns1 session create") { it is LocalSessionManagerEvent.SessionCreated && it.namespace == ns1Name },
                    TestEvent("ns1 destroy") { it is LocalSessionManagerEvent.NamespaceClosed && it.namespace == ns1Name },
                    TestEvent("ns1 session destroy") { it is LocalSessionManagerEvent.SessionClosed && it.namespace == ns1Name },
                )
            ) { flow ->
                val connection = CompletableDeferred<Unit>()
                val wsJob = launch {
                    val url = ktor.client.href(Events.WithToken.LsmEvents(Events.WithToken(token = authToken)))
                    ktor.client.webSocket("$url?namespace=$ns1Name") {
                        connection.complete(Unit)
                        incoming
                            .filterIsInstance<Frame.Text>(this@webSocket)
                            .map(this@webSocket) {
                                it.fromWsFrame<LocalSessionManagerEvent>()
                            }
                            .consumeEach {
                                println("morn $it")
                                flow.emit(it)
                            }
                    }
                }

                // post sessions after WS connection established
                connection.await()

                for (ns in listOf(ns1, ns2)) {
                    ktor.client.post(ns) {
                        withAuthToken()
                        contentType(ContentType.Application.Json)
                        setBody(
                            SessionRequest(
                                agentGraphRequest = AgentGraphRequest(
                                    agents = listOf(
                                        GraphAgentRequest(
                                            id = SeedDebugAgent.identifier,
                                            name = "seed",
                                            description = "",
                                            provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                            options = mapOf(
                                                "START_DELAY" to AgentOptionValue.UInt(1000u),
                                                "SEED_THREAD_COUNT" to AgentOptionValue.UInt(1u),
                                                "SEED_MESSAGE_COUNT" to AgentOptionValue.UInt(1u),
                                            )
                                        )
                                    ),
                                    groups = setOf(setOf("seed")),
                                )
                            )
                        )
                    }
                }

                wsJob
            }

            sessionManager.waitAllSessions()
            sessionManager.events.close()
            webSocketJob.join()
        }
    }
})

suspend inline fun <reified T> SessionTestScope.collectWsEvents(
    url: String,
    scope: CoroutineScope
): CompletableDeferred<List<T>> {
    val eventsDeferred = CompletableDeferred<List<T>>()
    val connection = CompletableDeferred<Unit>()

    scope.launch {
        ktor.client.webSocket(url) {
            connection.complete(Unit)
            eventsDeferred.complete(
                incoming
                    .filterIsInstance<Frame.Text>(this@webSocket)
                    .map(this@webSocket) {
                        it.fromWsFrame<T>()
                    }
                    .toList())
        }
    }
    connection.await()

    return eventsDeferred
}