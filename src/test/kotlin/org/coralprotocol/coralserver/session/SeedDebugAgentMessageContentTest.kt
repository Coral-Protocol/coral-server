package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.inspectors.forAllValues
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.debug.SEED_AGENT_IDENTIFIER
import org.coralprotocol.coralserver.dsl.sessionRequest
import org.coralprotocol.coralserver.routes.api.v1.LocalSessions
import org.koin.core.component.inject

/**
 * Covers the commit 99cb6cb4 change to `SeedDebugAgent.kt`: its `send_message` call site now wraps the
 * literal seed text in `SessionThreadMessagePart.Text(...)` instead of passing a bare `String`.
 *
 * `DebugAgentsTest.testSeedDebugAgent` already asserts message *counts* per thread, which the content-model
 * migration could not plausibly break (the call still produces exactly one [SessionThreadMessage] per
 * `sendMessageTool.executeOn` call regardless of what shape its `content` takes). It does not assert the
 * actual `content` value, so a regression that preserved message counts while corrupting, duplicating,
 * mistyping, or mis-indexing the wrapped text (e.g. `listOf(Text(""))`, `listOf(Text(x), Text(x))`, the
 * wrong message index, or a non-`Text` part) would pass that existing suite unnoticed. This test closes that
 * gap by asserting, per message, that `content` is exactly `listOf(SessionThreadMessagePart.Text("message
 * $index"))` for its position in the thread.
 */
class SeedDebugAgentMessageContentTest : CoralTest({
    test("testSeedDebugAgentMessageContent") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val threadCount = 2u
        val messageCount = 3u

        val sessionId: SessionIdentifier = client.authenticatedPost(LocalSessions.Session()) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(SEED_AGENT_IDENTIFIER) {
                        unsignedIntOption("SEED_THREAD_COUNT", threadCount)
                        unsignedIntOption("SEED_MESSAGE_COUNT", messageCount)
                    }
                    isolateAllAgents()
                }
            })
        }.shouldBeOK().body()

        val session = localSessionManager.getSessions(sessionId.namespace).firstOrNull().shouldNotBeNull()
        session.joinAgents()

        session.threads.shouldHaveSize(threadCount.toInt())
        session.threads.forAllValues { thread ->
            thread.withMessageLock { messages ->
                messages.shouldHaveSize(messageCount.toInt())

                messages.forEachIndexed { index, message ->
                    message.content shouldBe listOf(SessionThreadMessagePart.Text("message $index"))
                    message.senderName shouldBe "seed"
                }
            }
        }
    }
})
