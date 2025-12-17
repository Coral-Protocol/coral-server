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
import kotlinx.coroutines.channels.toList
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.routes.ws.v1.Events
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.session.models.SessionRequest
import org.coralprotocol.coralserver.util.filterIsInstance
import org.coralprotocol.coralserver.util.fromWsFrame
import org.coralprotocol.coralserver.util.map

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

            val resource = Events.WithToken.SessionEvents(Events.WithToken(token = authToken), id.namespace, id.sessionId)
            val eventsDeferred = CompletableDeferred<List<SessionEvent>>()
            ktor.client.webSocket(ktor.client.href(resource)) {
                eventsDeferred.complete(
                    incoming
                    .filterIsInstance<Frame.Text>(this@webSocket)
                    .map(this@webSocket) {
                        it.fromWsFrame<SessionEvent>()
                    }
                    .toList())
            }

            sessionManager.waitAllSessions()

            val events = eventsDeferred.await()
            val threadEvents = events.filterIsInstance<SessionEvent.ThreadCreated>()
            val messageEvents = events.filterIsInstance<SessionEvent.ThreadMessageSent>()
            threadEvents.shouldHaveSize(threadCount.toInt())
            messageEvents.shouldHaveSize(threadCount.toInt() * messageCount.toInt())

            events.shouldHaveEvents(
                mutableListOf(
                    ExpectedSessionEvent("agent connected") { it is SessionEvent.AgentConnected },
                    ExpectedSessionEvent("runtime stopped") { it is SessionEvent.RuntimeStopped },
                )
            )
        }
    }
})