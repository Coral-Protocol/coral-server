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
import org.coralprotocol.coralserver.agent.debug.EchoDebugAgent
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.utils.dsl.sessionRequest
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds

class DebugAgentsTest : CoralTest({
    test("testSeedDebugAgent").config(timeout = 60.seconds, tags = setOf(NamedTag("noisy"))) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
        val threadCount = 50u
        val messageCount = 100u

        val sessionId: SessionIdentifier = client.authenticatedPost(namespace) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(SeedDebugAgent.identifier) {
                        option("START_DELAY", AgentOptionValue.UInt(100u))
                        option("SEED_THREAD_COUNT", AgentOptionValue.UInt(threadCount))
                        option("SEED_MESSAGE_COUNT", AgentOptionValue.UInt(messageCount))
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

    test("testEchoDebugAgent").config(timeout = 30.seconds, tags = setOf(NamedTag("noisy"))) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
        val threadCount = 5u
        val messageCount = 10u

        val sessionId: SessionIdentifier = client.authenticatedPost(namespace) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(SeedDebugAgent.identifier) {
                        option("START_DELAY", AgentOptionValue.UInt(10u))
                        option("OPERATION_DELAY", AgentOptionValue.UInt(10u))
                        option("SEED_THREAD_COUNT", AgentOptionValue.UInt(threadCount))
                        option("SEED_MESSAGE_COUNT", AgentOptionValue.UInt(messageCount))
                        option("PARTICIPANTS", AgentOptionValue.StringList(listOf("echo")))
                        option("MENTIONS", AgentOptionValue.StringList(listOf("echo")))
                    }
                    agent(EchoDebugAgent.identifier) {
                        option("ITERATION_COUNT", AgentOptionValue.UInt(threadCount * messageCount))
                        option("FROM_AGENT", AgentOptionValue.String("seed"))
                        option("MENTIONS", AgentOptionValue.Boolean(true))
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