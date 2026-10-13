package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldExist
import io.kotest.matchers.shouldBe
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.dsl.graphAgentPair
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.logging.LoggingEvent
import org.coralprotocol.coralserver.modules.LOGGER_LOCAL_SESSION
import kotlinx.coroutines.cancel
import org.koin.core.qualifier.named
import org.koin.test.inject
import java.util.UUID

/**
 * Extracts the human-readable text of a [LoggingEvent], regardless of its concrete level. [LoggingEvent] does not
 * expose `text` on the sealed supertype itself, so every leaf case is matched explicitly here.
 */
private fun LoggingEvent.textOrNull(): String = when (this) {
    is LoggingEvent.Info -> text
    is LoggingEvent.Warning -> text
    is LoggingEvent.Debug -> text
    is LoggingEvent.Trace -> text
    is LoggingEvent.Error -> text
}

/**
 * Asserts [canary] never appears in any captured log line, to prove addMessage never
 * leaks raw message content into logs on any path.
 */
private fun Logger.assertNeverLogged(canary: String) {
    flow.replayCache.map { it.textOrNull() }.filter { canary in it }.shouldBeEmpty()
}

/**
 * Contract-focused tests for [SessionThread.addMessage], covering the shape change introduced in commit 99cb6cb4
 * (refactor: support rich content in send_message), which replaced a plain `String` message parameter with
 * `List<SessionThreadMessagePart>`.
 *
 * Per [SessionThread.addMessage]'s own documented contract (unchanged by that commit), these three exceptions are
 * specified independently of what `content` looks like:
 * - [SessionException.ThreadClosedException] when the thread is closed
 * - [SessionException.IllegalThreadMentionException] when the sender mentions themselves
 * - [SessionException.MissingAgentException] when a mentioned agent is not a thread participant
 *
 * Each is exercised below with both a populated, multi-shape `content` list and an empty one, to prove none of
 * these checks have become coupled to the size or shape of `content` now that it is a list instead of a string.
 *
 * Note: whether an accepted addMessage call should reject an empty content list is not
 * tested here. Neither issue #160 nor the approved plan specifies that behavior.
 */
class SessionThreadAddMessageTest : CoralTest({
    test("addMessage rejects a message into a closed thread regardless of content shape") {
        val localSessionManager by inject<LocalSessionManager>()
        val sessionLogger by inject<Logger>(named(LOGGER_LOCAL_SESSION))

        val session = localSessionManager.createSession(
            "ns",
            AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                )
            )
        ).first

        val agent1 = session.getAgent("agent1")
        val thread = session.createThread("Test thread", agent1.name, setOf("agent2"))
        thread.close(agent1, "closed for testing")

        val canary = "CANARY-${UUID.randomUUID()}"

        shouldThrow<SessionException.ThreadClosedException> {
            thread.addMessage(listOf(SessionThreadMessagePart.Text(canary)), agent1, setOf())
        }

        shouldThrow<SessionException.ThreadClosedException> {
            thread.addMessage(emptyList(), agent1, setOf())
        }

        thread.withMessageLock { it }.shouldBeEmpty()
        sessionLogger.assertNeverLogged(canary)

        session.sessionScope.cancel()
    }

    test("addMessage rejects a self-mention regardless of content shape") {
        val localSessionManager by inject<LocalSessionManager>()
        val sessionLogger by inject<Logger>(named(LOGGER_LOCAL_SESSION))

        val session = localSessionManager.createSession(
            "ns",
            AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                )
            )
        ).first

        val agent1 = session.getAgent("agent1")
        val thread = session.createThread("Test thread", agent1.name, setOf("agent2"))

        val canary = "CANARY-${UUID.randomUUID()}"

        shouldThrow<SessionException.IllegalThreadMentionException> {
            thread.addMessage(listOf(SessionThreadMessagePart.Text(canary)), agent1, setOf(agent1))
        }

        shouldThrow<SessionException.IllegalThreadMentionException> {
            thread.addMessage(emptyList(), agent1, setOf(agent1))
        }

        thread.withMessageLock { it }.shouldBeEmpty()
        sessionLogger.assertNeverLogged(canary)

        session.sessionScope.cancel()
    }

    test("addMessage rejects mentioning a non-participant regardless of content shape") {
        val localSessionManager by inject<LocalSessionManager>()
        val sessionLogger by inject<Logger>(named(LOGGER_LOCAL_SESSION))

        val session = localSessionManager.createSession(
            "ns",
            AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                    graphAgentPair("agent3"),
                )
            )
        ).first

        val agent1 = session.getAgent("agent1")
        val agent3 = session.getAgent("agent3")

        // agent3 deliberately left out of the thread's participants
        val thread = session.createThread("Test thread", agent1.name, setOf("agent2"))

        val canary = "CANARY-${UUID.randomUUID()}"

        shouldThrow<SessionException.MissingAgentException> {
            thread.addMessage(listOf(SessionThreadMessagePart.Text(canary)), agent1, setOf(agent3))
        }

        shouldThrow<SessionException.MissingAgentException> {
            thread.addMessage(emptyList(), agent1, setOf(agent3))
        }

        thread.withMessageLock { it }.shouldBeEmpty()
        sessionLogger.assertNeverLogged(canary)

        session.sessionScope.cancel()
    }

    test("addMessage stores every content part exactly as passed, in order, for a multi-part message") {
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

        val agent1 = session.getAgent("agent1")
        val agent2 = session.getAgent("agent2")
        val thread = session.createThread("Test thread", agent1.name, setOf(agent2.name))

        // one of every SessionThreadMessagePart/ResourceContents variant, with every optional field populated to a
        // distinct, non-default value, and a repeated Text part at both ends to catch ordering/truncation bugs that
        // a single instance of each type wouldn't expose (e.g. a wrong implementation that only kept content.first()
        // or content.last(), or that silently dropped every part except Text per asJsonState()'s own narrower
        // text-only summarization logic).
        val richContent = listOf(
            SessionThreadMessagePart.Text("first part: hello"),
            SessionThreadMessagePart.Image(data = "aW1hZ2ViYXNlNjQ=", mimeType = "image/png"),
            SessionThreadMessagePart.Audio(data = "YXVkaW9iYXNlNjQ=", mimeType = "audio/mpeg"),
            SessionThreadMessagePart.EmbeddedResource(
                resource = ResourceContents.Text(
                    text = "embedded resource text",
                    uri = "file:///embedded.txt",
                    mimeType = "text/plain"
                )
            ),
            SessionThreadMessagePart.EmbeddedResource(
                resource = ResourceContents.Blob(
                    blob = "YmxvYmJhc2U2NA==",
                    uri = "file:///embedded.bin",
                    mimeType = "application/octet-stream"
                )
            ),
            SessionThreadMessagePart.ResourceLink(
                uri = "file:///linked.txt",
                name = "linked.txt",
                mimeType = "text/plain",
                title = "Linked Document",
                description = "A linked resource with every optional field populated"
            ),
            SessionThreadMessagePart.Text("last part: goodbye"),
        )

        val returned = thread.addMessage(richContent, agent1, setOf(agent2))
        returned.content shouldContainExactly richContent

        // re-read the stored message, not the returned reference, to prove storage itself
        // preserves every part exactly
        val stored = thread.withMessageLock { messages -> messages.single() }
        stored.content shouldContainExactly richContent
        stored.id shouldBe returned.id

        session.sessionScope.cancel()
    }

    test("addMessage's success log line omits raw content but still records the send") {
        val localSessionManager by inject<LocalSessionManager>()
        val sessionLogger by inject<Logger>(named(LOGGER_LOCAL_SESSION))

        val session = localSessionManager.createSession(
            "ns",
            AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                )
            )
        ).first

        val agent1 = session.getAgent("agent1")
        val agent2 = session.getAgent("agent2")
        val thread = session.createThread("Test thread", agent1.name, setOf(agent2.name))

        val canary = "CANARY-${UUID.randomUUID()}"
        val msg = thread.addMessage(listOf(SessionThreadMessagePart.Text(canary)), agent1, setOf(agent2))

        // the defect this specifically guards against: commit 99cb6cb4 removed `"${message}"` from this exact log
        // line so that rich (and potentially large/sensitive) message content never lands in the server's logs. A
        // reintroduced interpolation of the content list (or of the whole message object, whose data-class toString
        // would also embed it) must fail this assertion.
        sessionLogger.assertNeverLogged(canary)

        // the send must still be observable in the logs, just without the raw content
        sessionLogger.flow.replayCache
            .filterIsInstance<LoggingEvent.Info>()
            .shouldExist { it.text.contains("(id=${msg.id})") && it.text.contains("into thread ${thread.id}") }

        session.sessionScope.cancel()
    }
})
