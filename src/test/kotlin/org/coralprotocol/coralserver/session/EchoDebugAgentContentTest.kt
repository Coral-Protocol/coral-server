package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.inspectors.forAllValues
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.debug.ECHO_AGENT_IDENTIFIER
import org.coralprotocol.coralserver.agent.debug.SEED_AGENT_IDENTIFIER
import org.coralprotocol.coralserver.dsl.sessionRequest
import org.coralprotocol.coralserver.routes.api.v1.LocalSessions
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

/**
 * Focused coverage for the `EchoDebugAgent` change in commit 99cb6cb4 (coral-server issue #160): the agent's
 * reply now must be constructed as `listOf(SessionThreadMessagePart.Text(...))` instead of a bare `String`.
 *
 * DebugAgentsTest only checks reply count, not shape. This proves each reply is exactly one
 * Text part, without asserting the literal reply string since that is not a documented contract.
 */
class EchoDebugAgentContentTest : CoralTest({
    test("testEchoDebugAgentWrapsRepliesAsSingleTextPart").config(invocationTimeout = 30.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val threadCount = 1u
        val messageCount = 3u

        val sessionId: SessionIdentifier = client.authenticatedPost(LocalSessions.Session()) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(SEED_AGENT_IDENTIFIER) {
                        unsignedIntOption("START_DELAY", 100u)
                        unsignedIntOption("OPERATION_DELAY", 50u)
                        unsignedIntOption("SEED_THREAD_COUNT", threadCount)
                        unsignedIntOption("SEED_MESSAGE_COUNT", messageCount)
                        stringListOption("PARTICIPANTS", "echo")
                        stringListOption("MENTIONS", "echo")
                    }
                    agent(ECHO_AGENT_IDENTIFIER) {
                        unsignedIntOption("ITERATION_COUNT", threadCount * messageCount)
                        stringOption("FROM_AGENT", "seed")
                        booleanOption("MENTIONS", true)
                    }
                    groupAllAgents()
                }
            })
        }.shouldBeOK().body()

        val session = localSessionManager.getSessions(sessionId.namespace).firstOrNull().shouldNotBeNull()
        session.joinAgents()

        session.threads.shouldHaveSize(threadCount.toInt())
        session.threads.forAllValues { thread ->
            thread.withMessageLock { messages ->
                val echoMessages = messages.filter { it.senderName == "echo" }

                // sanity check only, DebugAgentsTest already covers reply count
                echoMessages.shouldHaveSize(messageCount.toInt())

                // the actual focus: every echoed reply must be wrapped as exactly one non-blank text part,
                // per the new SessionThreadMessagePart content model this commit introduced for send_message
                echoMessages.forEach { echoMessage ->
                    echoMessage.content.shouldHaveSize(1)

                    val part = echoMessage.content.single()
                    part.shouldBeInstanceOf<SessionThreadMessagePart.Text>()
                    part.text.shouldNotBeBlank()
                }
            }
        }
    }
})
