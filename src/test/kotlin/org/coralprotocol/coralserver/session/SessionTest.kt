package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.NamedTag
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldExist
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.routes.sse.v1.Mcp
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.coralprotocol.coralserver.utils.synchronizedMessageTransaction
import org.koin.test.inject
import kotlin.time.Duration.Companion.seconds

class SessionTest : CoralTest({
    suspend fun HttpClient.sseHandshake(secret: String) {
        this.sse(this.href(Mcp.Sse(agentSecret = secret))) {
            // We will get a session so long as the agent secret is valid, the following line makes sure a connection
            // was established on the server by waiting for one message
            incoming.take(1).collect {}
        }
    }

    test("testLinks") {
        val localSessionManager by inject<LocalSessionManager>()

        val session1 = localSessionManager.createSession(
            "ns",
            AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                    graphAgentPair("agent3"),
                ),

                // no connection between agent1 and agent3
                groups = setOf(
                    setOf("agent1", "agent2"),
                    setOf("agent3", "agent2")
                )
            )
        ).first

        val session2 = localSessionManager.createSession(
            "ns",
            AgentGraph(
                mapOf(
                    graphAgentPair("agentA"),
                    graphAgentPair("agentB"),
                    graphAgentPair("agentC"),
                ),

                // every possible permutation of the same pairs
                groups = setOf(
                    setOf("agentA"),
                    setOf("agentB"),
                    setOf("agentC"),
                    setOf("agentA", "agentB"),
                    setOf("agentA", "agentC"),
                    setOf("agentB", "agentA"),
                    setOf("agentB", "agentC"),
                    setOf("agentC", "agentA"),
                    setOf("agentC", "agentB"),
                    setOf("agentA", "agentB", "agentC"),
                    setOf("agentA", "agentC", "agentB"),
                    setOf("agentB", "agentA", "agentC"),
                    setOf("agentB", "agentC", "agentA"),
                    setOf("agentC", "agentA", "agentB"),
                    setOf("agentC", "agentB", "agentA")
                )
            )
        ).first

        session1.hasLink("agent1", "agent2").shouldBeTrue()
        session1.hasLink("agent3", "agent2").shouldBeTrue()
        session1.hasLink("agent1", "agent3").shouldBeFalse()

        session2.hasLink("agentA", "agentB").shouldBeTrue()
        session2.hasLink("agentA", "agentC").shouldBeTrue()

        session2.hasLink("agentB", "agentC").shouldBeTrue()
        session2.hasLink("agentB", "agentA").shouldBeTrue()

        session2.hasLink("agentC", "agentB").shouldBeTrue()
        session2.hasLink("agentC", "agentA").shouldBeTrue()

        session2.agents["agentA"].shouldNotBeNull().links.shouldNotBeNull().shouldHaveSize(2)
        session2.agents["agentB"].shouldNotBeNull().links.shouldNotBeNull().shouldHaveSize(2)
        session2.agents["agentC"].shouldNotBeNull().links.shouldNotBeNull().shouldHaveSize(2)
    }

    test("testThreads") {
        val localSessionManager by inject<LocalSessionManager>()


        val session = localSessionManager.createSession(
            "ns",
            AgentGraph(
                mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                )
            )
        ).first

        // creates the first thread
        shouldNotThrowAny {
            session.createThread("Test thread", "agent1")
        }

        // creates the second thread
        shouldNotThrowAny {
            val thread = session.createThread("Test thread", "agent1", setOf("agent2"))
            thread.hasParticipant("agent2").shouldBeTrue()
            thread.hasParticipant("agent1").shouldBeTrue()
            thread.hasParticipant("agent100").shouldBeFalse()
        }

        // both fail, no threads created
        shouldThrow<SessionException.MissingAgentException> { session.createThread("Test thread", "agent100") }
        shouldThrow<SessionException.MissingAgentException> {
            session.createThread("Test thread", "agent1", setOf("agent1", "agent100"))
        }

        session.threads.shouldHaveSize(2)

    }

    test("testMessages") {
        val localSessionManager by inject<LocalSessionManager>()

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

        val agent1 = shouldNotThrowAny { session.getAgent("agent1") }
        val agent2 = shouldNotThrowAny { session.getAgent("agent2") }
        val agent3 = shouldNotThrowAny { session.getAgent("agent3") }

        val thread1 = shouldNotThrowAny {
            session.createThread("Test thread", agent1.name, setOf(agent2.name))
        }

        val thread2 = shouldNotThrowAny {
            session.createThread("Test thread", agent1.name, setOf(agent2.name, agent3.name))
        }

        shouldNotThrowAny {
            agent1.sendMessage("Hello from agent 1", thread1.id)
            agent2.sendMessage("Hello from agent 2", thread1.id)
        }

        // agent3 is not participating in thread1, which is the only thread with messages so far
        agent3.getVisibleMessages().shouldBeEmpty()

        agent1.getVisibleMessages().shouldHaveSize(2)
        agent2.getVisibleMessages().shouldHaveSize(2)

        thread1.close(agent1, "Nothing to see here...")

        shouldThrow<SessionException.ThreadClosedException> {
            agent1.sendMessage("Hello from agent 1", thread1.id)
        }

        // closing a thread should delete the messages
        agent1.getVisibleMessages().shouldBeEmpty()
        agent2.getVisibleMessages().shouldBeEmpty()

        shouldNotThrowAny {
            agent1.sendMessage("Hello from agent 1", thread2.id)
        }

    }

    test("testMentions").config(coroutineTestScope = true, tags = setOf(NamedTag("noisy"))) {
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

        val thread = shouldNotThrowAny {
            session.createThread("Test thread", "agent1", setOf("agent2"))
        }

        val otherThread = shouldNotThrowAny {
            session.createThread("Test thread 2", "agent1", setOf("agent2"))
        }

        val agent1 = shouldNotThrowAny {
            session.getAgent("agent1")
        }

        val agent2 = shouldNotThrowAny {
            session.getAgent("agent2")
        }

        // Ask for agent2 to wait for two messages now, waiting for messages will not return messages that were sent
        // before the agent begins waiting
        val messageText = "Hello world!"
        launch {
            val filters = setOf(
                SessionThreadMessageFilter.Thread(thread.id),
                SessionThreadMessageFilter.Mentions("agent2"),
                SessionThreadMessageFilter.From("agent1"),
            )

            agent2.waitForMessage(filters).shouldNotBeNull().text.shouldBeEqual(messageText)
            agent2.waitForMessage().shouldBeNull() // timeout
        }

        agent2.synchronizedMessageTransaction {
            // should be filtered: does not mention
            agent1.sendMessage("bad", thread.id)

            // should be filtered: in the wrong thread
            agent1.sendMessage("bad", otherThread.id, mentions = setOf("agent2"))

            // should be filtered: wrong sender (and wrong mentions)
            // checking channel buffer
            repeat(100_000) {
                agent2.sendMessage("bad $it", thread.id, mentions = setOf("agent1"))
            }

            // correct message
            agent1.sendMessage(messageText, thread.id, mentions = setOf("agent2")).id
        }

    }

    test("testBadSecret") {
        val client by inject<HttpClient>()

        shouldThrow<SSEClientException> { client.sseHandshake("bad-secret") }
    }

    test("testSseBlockingTimeout") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()

        val (session1, _) = localSessionManager.createSession(
            "ns",
            AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                ),
                groups = setOf(setOf("agent1", "agent2"))
            )
        )

        shouldNotThrowAny {
            val agent1 = session1.getAgent("agent1")

            // block because agent2 doesn't connect
            withTimeoutOrNull(1.seconds) {
                client.sseHandshake(agent1.secret)
            }.shouldBeNull()
        }
    }

    test("testSseChainBlockingTimeout") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()

        val (session1, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2"),
                    graphAgentPair("agent3"),
                ),
                groups = setOf(
                    setOf("agent1", "agent2"),
                    setOf("agent2", "agent3")
                )
            )
        )

        shouldNotThrowAny {
            val agent1 = session1.getAgent("agent1")
            val agent2 = session1.getAgent("agent2")

            // even though agent1 is only blocked by agent2, this should time out because agent3 never connects
            withTimeoutOrNull(1.seconds) {
                launch { client.sseHandshake(agent2.secret) }
                client.sseHandshake(agent1.secret)
            }.shouldBeNull()
        }
    }

    test("testSseBrokenChainBlockingTimeout") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()

        val (session1, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1"),
                    graphAgentPair("agent2") {
                        blocking = false
                    },
                    graphAgentPair("agent3"),
                ),
                groups = setOf(
                    setOf("agent1", "agent2"),
                    setOf("agent2", "agent3")
                )
            )
        )

        shouldNotThrowAny {
            val agent1 = session1.getAgent("agent1")

            // agent1 should have no reliance on agent3 because their common link is non-blocking
            withTimeoutOrNull(1.seconds) {
                client.sseHandshake(agent1.secret)
            }.shouldNotBeNull()
        }

    }

    test("testSseNonBlocking") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()

        val (session1, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1") {
                        blocking = false
                    },
                    graphAgentPair("agent2") {
                        blocking = false
                    },
                ),
                groups = setOf(setOf("agent1", "agent2"))
            )
        )

        shouldNotThrowAny {
            val agent1 = session1.getAgent("agent1")
            val agent2 = session1.getAgent("agent2")

            // neither agent is blocking
            withTimeoutOrNull(1.seconds) {
                client.sseHandshake(agent1.secret)
                client.sseHandshake(agent2.secret)
            }.shouldNotBeNull()
        }
    }

    test("testSseMcpTools") {
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()

        val (session1, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent1") {
                        blocking = false
                    },
                    graphAgentPair("agent2") {
                        blocking = false
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        plugin(GraphAgentPlugin.CloseSessionTool)
                    }
                ),
                groups = setOf(setOf("agent1", "agent2"))
            )
        )

        suspend fun toolsForAgent(name: String): ListToolsResult? {
            val agent1 = session1.getAgent(name)

            val mcpClient = Client(
                clientInfo = Implementation(
                    name = name,
                    version = "1.0.0"
                )
            )

            val transport = SseClientTransport(
                client = client,
                urlString = client.href(Mcp.Sse(agentSecret = agent1.secret))
            )
            mcpClient.connect(transport)
            return mcpClient.listTools()
        }

        shouldNotThrowAny {
            toolsForAgent("agent1")
                .shouldNotBeNull()
                .tools
                .shouldForAll {
                    it.name != McpToolName.CLOSE_SESSION.toString()
                }

            toolsForAgent("agent2")
                .shouldNotBeNull()
                .tools
                .shouldExist {
                    it.name == McpToolName.CLOSE_SESSION.toString()
                }
        }
    }
})