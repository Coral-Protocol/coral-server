package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.core.NamedTag
import io.kotest.inspectors.forAllValues
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
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

class DebugAgentsTest : CoralTest({
    test("testSeedDebugAgent").config(invocationTimeout = 60.seconds, tags = setOf(NamedTag("noisy"))) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val threadCount = 50u
        val messageCount = 100u

        val sessionId: SessionIdentifier = client.authenticatedPost(LocalSessions.Session()) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(SEED_AGENT_IDENTIFIER) {
                        unsignedIntOption("START_DELAY", 100u)
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
        session.threads.forAllValues {
            it.withMessageLock { messages ->
                messages.shouldHaveSize(messageCount.toInt())
            }
        }
    }

    test("testEchoDebugAgent").config(invocationTimeout = 30.seconds, tags = setOf(NamedTag("noisy"))) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val threadCount = 1u
        val messageCount = 50u

        val sessionId: SessionIdentifier = client.authenticatedPost(LocalSessions.Session()) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(SEED_AGENT_IDENTIFIER) {
                        unsignedIntOption("START_DELAY", 100u)
                        unsignedIntOption("OPERATION_DELAY", 200u)
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
                // one message from seed
                messages.filter { it.senderName == "seed" }.shouldHaveSize(messageCount.toInt())

                // one response from echo
                messages.filter { it.senderName == "echo" }.shouldHaveSize(messageCount.toInt())
            }
        }
    }
})
