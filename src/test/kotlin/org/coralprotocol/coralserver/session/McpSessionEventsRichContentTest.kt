package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.matchers.collections.shouldContainExactly
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.debug.PUPPET_AGENT_IDENTIFIER
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.dsl.sessionRequest
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.mcp.tools.*
import org.coralprotocol.coralserver.modules.WEBSOCKET_COROUTINE_SCOPE_NAME
import org.coralprotocol.coralserver.routes.api.v1.LocalSessions
import org.coralprotocol.coralserver.routes.api.v1.Puppet
import org.coralprotocol.coralserver.routes.ws.v1.Events
import org.coralprotocol.coralserver.util.filterIsInstance
import org.coralprotocol.coralserver.util.fromWsFrame
import org.coralprotocol.coralserver.util.map
import org.coralprotocol.coralserver.utils.TestEvent
import org.coralprotocol.coralserver.utils.shouldPostEventsFromBody
import org.koin.core.qualifier.named
import org.koin.test.inject
import kotlin.time.Duration.Companion.seconds

/**
 * No existing test drives a ThreadMessageSent event through the real WebSocket wire
 * format with more than one Text part. This sends a message with every content kind,
 * receives it over a real WebSocket connection, and checks every field round trips.
 */
class McpSessionEventsRichContentTest : CoralTest({
    test("testThreadMessageSentRichContentRoundTripsOverRealWebSocket").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val json by inject<Json>()
        val websocketCoroutineScope by inject<CoroutineScope>(named(WEBSOCKET_COROUTINE_SCOPE_NAME))

        val namespaceName = "default"
        val agentName = "puppet1"
        val threadName = "rich content thread"

        // Every content kind, both ResourceContents shapes, and a ResourceLink tried both
        // fully populated and fully default.
        val richContent = listOf(
            SessionThreadMessagePart.Text("hello from the rich content test"),
            SessionThreadMessagePart.Image(data = "aGVsbG8gaW1hZ2U=", mimeType = "image/png"),
            SessionThreadMessagePart.Audio(data = "aGVsbG8gYXVkaW8=", mimeType = "audio/mpeg"),
            SessionThreadMessagePart.EmbeddedResource(
                resource = ResourceContents.Text(
                    text = "embedded text resource contents",
                    uri = "coral://resource/text",
                    mimeType = "text/plain"
                )
            ),
            SessionThreadMessagePart.EmbeddedResource(
                resource = ResourceContents.Blob(
                    blob = "aGVsbG8gYmxvYg==",
                    uri = "coral://resource/blob",
                    mimeType = "application/octet-stream"
                )
            ),
            SessionThreadMessagePart.ResourceLink(
                uri = "https://example.com/report.pdf",
                name = "report.pdf",
                mimeType = "application/pdf",
                title = "Quarterly report",
                description = "a fully-populated resource link"
            ),
            SessionThreadMessagePart.ResourceLink(
                uri = "https://example.com/minimal",
                name = "minimal-link"
                // mimeType/title/description deliberately left at their null defaults: the shared Json bean used
                // by every WS route (see CoralTest) sets explicitNulls = false, so this also proves optional-field
                // absence survives the real encode/decode round trip, not just presence.
            ),
        )

        val id: SessionIdentifier = client.authenticatedPost(LocalSessions.Session()) {
            setBody(
                sessionRequest {
                    createNamespaceIfNotExists {
                        name = namespaceName
                    }
                    agentGraphRequest {
                        agent(PUPPET_AGENT_IDENTIFIER) {
                            name = agentName
                            provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        }
                        isolateAllAgents()
                    }
                }
            )
        }.body()

        val session = localSessionManager.getSessions(namespaceName).find { it.id == id.sessionId }
            ?: error("session ${id.sessionId} was not created")

        val agent = Puppet.Agent(namespace = id.namespace, sessionId = id.sessionId, agentName = agentName)
        val threadRoute = Puppet.Agent.Thread(agent)

        val capturedMessage = CompletableDeferred<SessionThreadMessage>()

        val webSocketJob = this.shouldPostEventsFromBody(
            timeout = 10.seconds,
            allowUnexpectedEvents = true,
            events = mutableListOf<TestEvent<SessionEvent>>(
                TestEvent("thread '$threadName' created") { it is SessionEvent.ThreadCreated },
                TestEvent("rich content message sent") { event ->
                    if (event is SessionEvent.ThreadMessageSent) {
                        capturedMessage.complete(event.message)
                        true
                    } else {
                        false
                    }
                },
            )
        ) { flow ->
            val wsJob = launch {
                val url = client.href(
                    Events.WithToken.SessionEvents(
                        Events.WithToken(token = authToken),
                        id.namespace,
                        id.sessionId
                    )
                )
                client.webSocket(url) {
                    incoming
                        .filterIsInstance<Frame.Text>(this@webSocket)
                        .map(this@webSocket) {
                            it.fromWsFrame<SessionEvent>(json)
                        }
                        .consumeEach {
                            flow.emit(it)
                        }
                }
            }

            // Make sure the real WebSocket is actually subscribed server-side before triggering the events we
            // expect it to deliver, to avoid a race where the message is sent (and the event already gone) before
            // we connect.
            session.events.subscriptionCount.first { it == 1 }

            val createThreadResponse: CreateThreadOutput = client.authenticatedPost(threadRoute) {
                setBody(CreateThreadInput(threadName = threadName, participantNames = listOf()))
            }.shouldBeOK().body()

            client.authenticatedPost(Puppet.Agent.Thread.Message(threadRoute)) {
                setBody(
                    SendMessageInput(
                        threadId = createThreadResponse.thread.id,
                        content = richContent,
                        mentions = listOf()
                    )
                )
            }.shouldBeOK()

            wsJob
        }

        webSocketJob.cancelAndJoin()

        // The real oracle: decode(encode(x)) == x, for every part/kind/field, over the real wire format.
        val received = capturedMessage.await()
        received.content shouldContainExactly richContent

        client.authenticatedDelete(agent).shouldBeOK()
        localSessionManager.waitAllSessions()
        websocketCoroutineScope.cancel()
    }
})
