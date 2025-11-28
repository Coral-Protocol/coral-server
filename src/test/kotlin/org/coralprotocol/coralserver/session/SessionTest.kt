package org.coralprotocol.coralserver.session

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryAgentInfo
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.routes.sse.v1.Mcp
import org.coralprotocol.coralserver.routes.sse.v1.mcpRoutes
import org.coralprotocol.coralserver.server.apiJsonConfig
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class SessionTest {
    private val sessionManager = LocalSessionManager(null, JupiterService())

    fun dummyRegistryAgent(name: String) =
        RegistryAgent(
            info = RegistryAgentInfo(
                name = name,
                version = "1.0.0",
                description = "Test agent",
                capabilities = setOf()
            ),
            runtimes = LocalAgentRuntimes(),
            path = Path.of(System.getProperty("user.dir")),
            unresolvedExportSettings = mapOf(),
            options = mapOf()
        )

    fun dummyGraphAgent(name: String, blocking: Boolean = true) =
        GraphAgent(
            registryAgent = dummyRegistryAgent(name),
            name = name,
            description = "Test agent",
            options = mapOf(),
            systemPrompt = null,
            blocking = blocking,
            customToolAccess = setOf(),
            plugins = setOf(),
            provider = GraphAgentProvider.Local(
                runtime = RuntimeId.FUNCTION
            ),
            x402Budgets = listOf(),
        )

    suspend fun graphToSession(agentGraph: AgentGraph) =
        sessionManager.createSession("test namespace", agentGraph)

    private fun sseEnv(body: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            application {
                install(io.ktor.server.resources.Resources)
                routing { mcpRoutes(sessionManager) }
                install(ContentNegotiation) {
                    json(apiJsonConfig, contentType = ContentType.Application.Json)
                }
            }

            client = createClient {
                install(Resources)
                install(SSE)
            }

            body()
        }
    }

    private suspend fun HttpClient.agentSseConnection(secret: String) {
        this.sse(this.href(Mcp.Sse(secret))) {
            // We will get a session so long as the agent secret is valid, the following line makes sure a connection
            // was established on the server by waiting for one message
            incoming.take(1).collect {}
        }
    }

    @Test
    fun testLinks() = runTest {
        val session1 = graphToSession(AgentGraph(
            agents = mapOf(
                "agent1" to dummyGraphAgent("agent1"),
                "agent2" to dummyGraphAgent("agent2"),
                "agent3" to dummyGraphAgent("agent3"),
            ),
            customTools = mapOf(),

            // no connection between agent1 and agent3
            groups = setOf(
                setOf("agent1", "agent2"),
                setOf("agent3", "agent2")
            )
        ))

        val session2 = graphToSession(AgentGraph(
            agents = mapOf(
                "agentA" to dummyGraphAgent("agentA"),
                "agentB" to dummyGraphAgent("agentB"),
                "agentC" to dummyGraphAgent("agentC"),
            ),
            customTools = mapOf(),

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
        ))

        assert(session1.hasLink("agent1", "agent2"))
        assert(session1.hasLink("agent3", "agent2"))
        assert(!session1.hasLink("agent1", "agent3"))

        assert(session2.hasLink("agentA", "agentB"))
        assert(session2.hasLink("agentA", "agentC"))

        assert(session2.hasLink("agentB", "agentC"))
        assert(session2.hasLink("agentB", "agentA"))

        assert(session2.hasLink("agentC", "agentB"))
        assert(session2.hasLink("agentC", "agentA"))

        assert(session2.agents["agentA"]?.links?.size == 2)
        assert(session2.agents["agentB"]?.links?.size == 2)
        assert(session2.agents["agentC"]?.links?.size == 2)
    }

    @Test
    fun threadTest() = runTest {
        val session = graphToSession(AgentGraph(
            agents = mapOf(
                "agent1" to dummyGraphAgent("agent1"),
                "agent2" to dummyGraphAgent("agent2"),
            ),
            customTools = mapOf(),
            groups = setOf()
        ))

        // creates the first thread
        shouldNotThrowAny {
            session.createThread("Test thread", "agent1")
        }

        // creates the second thread
        shouldNotThrowAny {
            run {
                val thread = session.createThread("Test thread", "agent1", setOf("agent2"))
                assert(thread.participants.contains("agent2"))
                assert(thread.participants.contains("agent1"))
                assert(!thread.participants.contains("agent100"))
            }
        }

        // both fail, no threads created
        shouldThrow<SessionException.MissingAgentException> { session.createThread("Test thread", "agent100") }
        shouldThrow<SessionException.MissingAgentException> {
            session.createThread("Test thread", "agent1", setOf("agent1", "agent100"))
        }

        assert(session.threads.size == 2)
    }

    @Test
    fun messageTest() = runTest {
        val session = graphToSession(AgentGraph(
            agents = mapOf(
                "agent1" to dummyGraphAgent("agent1"),
                "agent2" to dummyGraphAgent("agent2"),
                "agent3" to dummyGraphAgent("agent3"),
            ),
            customTools = mapOf(),
            groups = setOf()
        ))

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
        assert(agent3.getVisibleMessages().isEmpty())

        assert(agent1.getVisibleMessages().size == 2)
        assert(agent2.getVisibleMessages().size == 2)

        thread1.close("Nothing to see here...")

        shouldThrow<SessionException.ThreadClosedException> {
            agent1.sendMessage("Hello from agent 1", thread1.id)
        }

        // closing a thread should delete the messages
        assert(agent1.getVisibleMessages().isEmpty())
        assert(agent2.getVisibleMessages().isEmpty())

        shouldNotThrowAny {
            agent1.sendMessage("Hello from agent 1", thread2.id)
        }
    }

    @Test
    fun mentionTest() = runTest(timeout = 5.seconds) {
        val session = graphToSession(AgentGraph(
            agents = mapOf(
                "agent1" to dummyGraphAgent("agent1"),
                "agent2" to dummyGraphAgent("agent2"),
            ),
            customTools = mapOf(),
            groups = setOf()
        ))

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
        val waitTest = launch {
            val filters = setOf(
                SessionThreadMessageFilter.Thread(thread.id),
                SessionThreadMessageFilter.Mentions("agent2"),
                SessionThreadMessageFilter.From("agent1"),
            )
            assert(agent2.waitForMessage(filters)?.text == messageText)

            // should timeout (note that virtual test time here is used)
            assert(agent2.waitForMessage() == null)
        }

        // need to be sure that waitForMessage is actually waiting before sending a message to it
        // (simulated delay in tests)
        delay(1000)

        shouldNotThrowAny {
            // should be filtered: does not mention
            agent1.sendMessage("bad", thread.id)

            // should be filtered: in the wrong thread
            agent1.sendMessage("bad", otherThread.id, mentions = setOf("agent2"))

            // should be filtered: wrong sender (and wrong mentions)
            // checking channel buffer
            (1..100_000).forEach { i ->
                agent1.sendMessage("bad", thread.id, mentions = setOf("agent2"))
            }

            // just right
            agent1.sendMessage(messageText, thread.id, mentions = setOf("agent2"))
        }

        waitTest.join()
    }

    @Test
    fun sseBadSecret() = sseEnv {
        shouldThrow<SSEClientException> { client.agentSseConnection("bad-secret") }
    }

    @Test
    fun sseBlockingTimeout() = sseEnv {
        withContext(Dispatchers.IO) {
            withTimeout(timeout = 5.seconds) {
                val session1 = sessionManager.createSession("test", AgentGraph(
                    agents = mapOf(
                        "agent1" to dummyGraphAgent("agent1"),
                        "agent2" to dummyGraphAgent("agent2"),
                    ),
                    customTools = mapOf(),
                    groups = setOf(setOf("agent1", "agent2"))
                ))

                shouldNotThrowAny {
                    val agent1 = session1.getAgent("agent1")

                    // should time out because agent2 never connects
                    assert(withTimeoutOrNull(1000) {
                        client.agentSseConnection(agent1.secret)
                    } == null)
                }
            }
        }
    }

    @Test
    fun sseChainBlockingTimeout() = sseEnv {
        withContext(Dispatchers.IO) {
            withTimeout(timeout = 5.seconds) {
                val session1 = sessionManager.createSession("test", AgentGraph(
                    agents = mapOf(
                        "agent1" to dummyGraphAgent("agent1"),
                        "agent2" to dummyGraphAgent("agent2"),
                        "agent3" to dummyGraphAgent("agent3"),
                    ),
                    customTools = mapOf(),
                    groups = setOf(
                        setOf("agent1", "agent2"),
                        setOf("agent2", "agent3")
                    )
                ))

                shouldNotThrowAny {
                    val agent1 = session1.getAgent("agent1")
                    val agent2 = session1.getAgent("agent2")

                    // even though agent1 is only blocked by agent2, this should time out because agent3 never connects
                    assert(withTimeoutOrNull(1000) {
                        launch { client.agentSseConnection(agent2.secret) }
                        client.agentSseConnection(agent1.secret)
                    } == null)
                }
            }
        }
    }

    @Test
    fun sseBrokenChainBlockingTimeout() = sseEnv {
        withContext(Dispatchers.IO) {
            withTimeout(timeout = 5.seconds) {
                val session1 = sessionManager.createSession("test", AgentGraph(
                    agents = mapOf(
                        "agent1" to dummyGraphAgent("agent1"),
                        "agent2" to dummyGraphAgent("agent2", false),
                        "agent3" to dummyGraphAgent("agent3"),
                    ),
                    customTools = mapOf(),
                    groups = setOf(
                        setOf("agent1", "agent2"),
                        setOf("agent2", "agent3")
                    )
                ))

                shouldNotThrowAny {
                    val agent1 = session1.getAgent("agent1")

                    // agent1 should have no reliance on agent3 because their common link is non-blocking
                    assert(withTimeoutOrNull(1000) {
                        client.agentSseConnection(agent1.secret)
                    } != null)
                }
            }
        }
    }

    @Test
    fun sseNonBlockingTest() = sseEnv {
        withContext(Dispatchers.IO) {
            withTimeout(timeout = 5.seconds) {
                val session1 = sessionManager.createSession("test", AgentGraph(
                    agents = mapOf(
                        "agent1" to dummyGraphAgent("agent1", false),
                        "agent2" to dummyGraphAgent("agent2", false),
                    ),
                    customTools = mapOf(),
                    groups = setOf(setOf("agent1", "agent2"))
                ))

                shouldNotThrowAny {
                    val agent1 = session1.getAgent("agent1")
                    val agent2 = session1.getAgent("agent2")

                    // neither agent is blocking
                    assert(withTimeoutOrNull(1000) {
                        client.agentSseConnection(agent1.secret)
                        client.agentSseConnection(agent2.secret)
                    } != null)
                }
            }
        }
    }

    @Test
    fun sseMcpTools() = sseEnv {
        withContext(Dispatchers.IO) {
            withTimeout(timeout = 5.seconds) {
                val session1 = sessionManager.createSession("test", AgentGraph(
                    agents = mapOf(
                        "agent1" to dummyGraphAgent("agent1", false),
                    ),
                    customTools = mapOf(),
                    groups = setOf(setOf("agent1", "agent2"))
                ))

                shouldNotThrowAny {
                    val agent1 = session1.getAgent("agent1")

                    val mcpClient = Client(
                        clientInfo = Implementation(
                            name = "test",
                            version = "1.0.0"
                        )
                    )

                    val transport = SseClientTransport(
                        client = client,
                        urlString = client.href(Mcp.Sse(agent1.secret))
                    )
                    mcpClient.connect(transport)

                    // Verify connection by testing for tool presence
                    // todo: request a specific tool and check for that tool's presence
                    val toolResult = mcpClient.listTools()
                    assertNotNull(toolResult)
                    assert(toolResult.tools.isNotEmpty())
                }
            }
        }
    }
}