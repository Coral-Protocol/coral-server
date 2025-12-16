package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAllValues
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.coralprotocol.coralserver.agent.debug.EchoDebugAgent
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.session.models.SessionRequest
import kotlin.time.Duration.Companion.seconds

class DebugAgentsTest : FunSpec({
    test("testSeedDebugAgent").config(timeout = 20.seconds) {
        sessionTest({
            addLocalAgents(
                listOf(
                    SeedDebugAgent(it.client).generate(),
                ), "debug agents"
            )
        }) {
            val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
            val threadCount = 50u
            val messageCount = 100u

            val sessionId: SessionIdentifier = ktor.client.post(namespace) {
                contentType(ContentType.Application.Json)
                setBody(
                    SessionRequest(
                        agentGraphRequest =
                            AgentGraphRequest(
                                agents = listOf(
                                    GraphAgentRequest(
                                        id = SeedDebugAgent.identifier,
                                        name = "seed",
                                        description = "",
                                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                        options = mapOf(
                                            "START_DELAY" to AgentOptionValue.UInt(100u),
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

            val session = sessionManager.getSessions(sessionId.namespace).firstOrNull().shouldNotBeNull()
            session.joinAgents()

            session.threads.shouldHaveSize(threadCount.toInt())
            session.threads.forAllValues {
                it.withMessageLock { messages ->
                    messages.shouldHaveSize(messageCount.toInt())
                }
            }
        }
    }

    test("testEchoDebugAgent").config(timeout = 10.seconds) {
        sessionTest({
            addLocalAgents(
                listOf(
                    SeedDebugAgent(it.client).generate(),
                    EchoDebugAgent(it.client).generate(),
                ), "debug agents"
            )
        }) {
            val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
            val threadCount = 5u
            val messageCount = 10u

            val sessionId: SessionIdentifier = ktor.client.post(namespace) {
                contentType(ContentType.Application.Json)
                setBody(
                    SessionRequest(
                        agentGraphRequest =
                            AgentGraphRequest(
                                agents = listOf(
                                    GraphAgentRequest(
                                        id = SeedDebugAgent.identifier,
                                        name = "seed",
                                        description = "",
                                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                        options = mapOf(
                                            "START_DELAY" to AgentOptionValue.UInt(10u), // start delay required so that echo has a chance start listening
                                            "OPERATION_DELAY" to AgentOptionValue.UInt(10u), // operation delay required so that echo has a change to wait again
                                            "SEED_THREAD_COUNT" to AgentOptionValue.UInt(threadCount),
                                            "SEED_MESSAGE_COUNT" to AgentOptionValue.UInt(messageCount),
                                            "PARTICIPANTS" to AgentOptionValue.StringList(listOf("echo")),
                                            "MENTIONS" to AgentOptionValue.StringList(listOf("echo")),
                                        )
                                    ),
                                    GraphAgentRequest(
                                        id = EchoDebugAgent.identifier,
                                        name = "echo",
                                        description = "",
                                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                        options = mapOf(
                                            "ITERATION_COUNT" to AgentOptionValue.UInt(threadCount * messageCount),
                                            "FROM_AGENT" to AgentOptionValue.String("seed"),
                                            "MENTIONS" to AgentOptionValue.Boolean(true),
                                        )
                                    )
                                ),
                                groups = setOf(setOf("seed", "echo")),
                            )
                    )
                )
            }.body()

            val session = sessionManager.getSessions(sessionId.namespace).firstOrNull().shouldNotBeNull()
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
    }
})