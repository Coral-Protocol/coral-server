package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.coroutines.cancel
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.dsl.graphAgentPair
import org.koin.test.inject

/**
 * Focused regression coverage for [SessionAgent.sendMessage]'s content-list contract, introduced when
 * `send_message`'s content moved from a plain `String` to `List<SessionThreadMessagePart>` (coral-server
 * issue #160).
 *
 * sendMessage just forwards content to addMessage. Existing tests only use single-element
 * Text lists, which cannot catch a wrong implementation that truncates, filters, reorders,
 * or deduplicates parts.
 *
 * These tests close that gap by round-tripping a heterogeneous, multi-element, order-and-duplicate-bearing
 * list through the real [SessionAgent.sendMessage] entry point and asserting full structural equality on
 * what comes back, plus the `n-1` empty-list boundary.
 */
class SessionAgentSendMessageContentTest : CoralTest({
    test("sendMessage forwards a heterogeneous multi-part content list unchanged and in order") {
        val localSessionManager by inject<LocalSessionManager>()

        val session = localSessionManager.createSession(
            "ns",
            AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                )
            )
        ).first

        val agent1 = shouldNotThrowAny { session.getAgent("agent1") }
        val agent2 = shouldNotThrowAny { session.getAgent("agent2") }

        val thread = shouldNotThrowAny {
            session.createThread("Test thread", agent1.name, setOf(agent2.name))
        }

        // Exercises all five SessionThreadMessagePart variants, both ResourceContents variants nested
        // inside EmbeddedResource, a repeated/duplicate element (probes accidental Set-like deduplication),
        // and a deliberately non-alphabetical, non-trivial order (probes accidental reordering/sorting).
        val content = listOf(
            SessionThreadMessagePart.Text("first part"),
            SessionThreadMessagePart.Image(data = "aW1hZ2ViYXNlNjQ=", mimeType = "image/png"),
            SessionThreadMessagePart.Audio(data = "YXVkaW9iYXNlNjQ=", mimeType = "audio/mpeg"),
            SessionThreadMessagePart.EmbeddedResource(
                resource = ResourceContents.Text(
                    text = "embedded text contents",
                    uri = "file:///doc.txt",
                    mimeType = "text/plain"
                )
            ),
            SessionThreadMessagePart.EmbeddedResource(
                resource = ResourceContents.Blob(
                    blob = "YmxvYmJhc2U2NA==",
                    uri = "file:///doc.bin",
                    mimeType = "application/octet-stream"
                )
            ),
            SessionThreadMessagePart.ResourceLink(
                uri = "https://example.com/resource",
                name = "example-resource",
                mimeType = "application/json",
                title = "Example Resource",
                description = "A linked resource"
            ),
            // duplicate of the first element, at a different position
            SessionThreadMessagePart.Text("first part"),
        )

        val sent = shouldNotThrowAny { agent1.sendMessage(content, thread.id) }

        // Full structural (order-sensitive, count-sensitive, field-sensitive) equality: a wrong
        // implementation that truncates, filters, reorders, or deduplicates would fail this.
        sent.content shouldBeEqual content

        // The same content must also survive the thread's storage/visibility path when read back through
        // another participant, reached only via this sendMessage call.
        val visible = agent2.getVisibleMessages()
        visible shouldHaveSize 1
        visible.single().content shouldBeEqual content

        session.sessionScope.cancel()
    }

    test("sendMessage accepts an empty content list") {
        val localSessionManager by inject<LocalSessionManager>()

        val session = localSessionManager.createSession(
            "ns",
            AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                )
            )
        ).first

        val agent1 = shouldNotThrowAny { session.getAgent("agent1") }
        val agent2 = shouldNotThrowAny { session.getAgent("agent2") }

        val thread = shouldNotThrowAny {
            session.createThread("Test thread", agent1.name, setOf(agent2.name))
        }

        val sent = shouldNotThrowAny { agent1.sendMessage(emptyList(), thread.id) }

        sent.content.shouldBeEmpty()

        val visible = agent2.getVisibleMessages()
        visible shouldHaveSize 1
        visible.single().content.shouldBeEmpty()

        session.sessionScope.cancel()
    }
})
