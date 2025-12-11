package org.coralprotocol.coralserver.session

import DockerRuntime
import io.kotest.assertions.asClue
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryAgentInfo
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.*
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.routes.sse.v1.Mcp
import org.coralprotocol.coralserver.routes.sse.v1.mcpRoutes
import org.coralprotocol.coralserver.server.apiJsonConfig
import java.nio.file.Path
import kotlin.time.Duration
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class SessionTestScope(
    val testScope: TestScope,
    val ktor: ApplicationTestBuilder,
) {
    val config = Config(
        // port for testing is zero
        networkConfig = NetworkConfig(
            bindPort = 0u
        )
    )
    val applicationRuntimeContext = ApplicationRuntimeContext(config)
    val mcpToolManager = McpToolManager()
    val jupiterService = JupiterService()
    val sessionManager = LocalSessionManager(
        blockchainService = null,
        applicationRuntimeContext = applicationRuntimeContext,
        jupiterService = jupiterService,
        mcpToolManager = mcpToolManager,
        managementScope = testScope,

        // if this is true, exceptions thrown (including assertions) in an agent's runtime will not exit a test
        // it also requires that session's coroutine scopes are canceled
        supervisedSessions = false
    )

    fun registryAgent(
        name: String = "TestAgent",
        version: String = "1.0.0",
        description: String = "Test agent",
        executableRuntime: ExecutableRuntime? = null,
        functionRuntime: FunctionRuntime? = null,
        dockerRuntime: DockerRuntime? = null
    ) =
        RegistryAgent(
            info = RegistryAgentInfo(
                name = name,
                version = version,
                description = description,
                capabilities = setOf()
            ),
            runtimes = LocalAgentRuntimes(
                executableRuntime = executableRuntime,
                functionRuntime = functionRuntime,
                dockerRuntime = dockerRuntime
            ),
            path = Path.of(System.getProperty("user.dir")),
            unresolvedExportSettings = mapOf(),
            options = mapOf()
        )

    fun graphAgent(
        registryAgent: RegistryAgent = registryAgent(),
        blocking: Boolean = true,
        provider: GraphAgentProvider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
        options: Map<String, AgentOptionWithValue> = mapOf(),
        plugins: Set<GraphAgentPlugin> = setOf(),
    ) =
        registryAgent.info.name to GraphAgent(
            registryAgent = registryAgent,
            name = registryAgent.info.name,
            description = registryAgent.info.description,
            options = options,
            systemPrompt = null,
            blocking = blocking,
            customToolAccess = setOf(),
            plugins = plugins,
            provider = provider,
            x402Budgets = listOf(),
        )

    fun graphAgent(
        name: String,
        blocking: Boolean = true,
        provider: GraphAgentProvider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
    ) =
        graphAgent(
            registryAgent = registryAgent(name = name),
            blocking = blocking,
            provider = provider
        )

    fun HttpClient.mcpFunctionRuntime(name: String, func: suspend (Client, LocalSession) -> Unit) =
        FunctionRuntime { executionContext, applicationRuntimeContext ->
            val mcpClient = Client(
                clientInfo = Implementation(
                    name = name,
                    version = "1.0.0"
                )
            )

            val transport = SseClientTransport(
                client = this,
                urlString = applicationRuntimeContext.getMcpUrl(
                    executionContext,
                    AddressConsumer.LOCAL
                ).toString()
            )
            mcpClient.connect(transport)
            func(mcpClient, executionContext.session)
        }

    suspend fun HttpClient.sseHandshake(secret: String) {
        this.sse(this.href(Mcp.Sse(secret))) {
            // We will get a session so long as the agent secret is valid, the following line makes sure a connection
            // was established on the server by waiting for one message
            incoming.take(1).collect {}
        }
    }

    suspend fun buildSession(agents: Map<UniqueAgentName, suspend (Client, LocalSession) -> Unit>) {
        val (session, _) = sessionManager.createSession(
            "test", AgentGraph(
                agents = agents.mapValues { (name, func) ->
                    graphAgent(
                        registryAgent = registryAgent(
                            name = name,
                            functionRuntime = ktor.client.mcpFunctionRuntime(name, func)
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    ).second
                },
                customTools = mapOf(),
                groups = setOf(agents.keys.toSet())
            )
        )

        session.launchAgents()
        session.joinAgents()
    }

    suspend fun graphToSession(graph: AgentGraph) =
        sessionManager.createSession("graphToSession", graph).first
}

suspend fun TestScope.sessionTest(test: suspend SessionTestScope.() -> Unit) {
    runTestApplication(coroutineContext) {
        val sessionTestScope = SessionTestScope(this@sessionTest, this);

        application {
            install(io.ktor.server.resources.Resources)
            routing { mcpRoutes(sessionTestScope.sessionManager) }
            install(ServerContentNegotiation) {
                json(apiJsonConfig, contentType = ContentType.Application.Json)
            }
        }

        client = createClient {
            install(Resources)
            install(SSE)
            install(ClientContentNegotiation) {
                json(apiJsonConfig, contentType = ContentType.Application.Json)
            }
        }

        sessionTestScope.test()
    }
}

suspend fun SessionAgent.synchronizedMessageTransaction(sendMessageFn: suspend () -> MessageId) {
    val waiter = waiters.first { !it.deferred.isCompleted }

    val msgId = sendMessageFn()
    val returnedMsg = waiter.deferred.await()

    if (returnedMsg.id != msgId)
        throw IllegalStateException("$name's active waiter returned message ${returnedMsg.id} instead of expected $msgId")
}

data class ExpectedSessionEvent(
    val description: String,
    val predicate: (event: SessionEvent) -> Boolean
)

suspend fun LocalSession.shouldPostEvents(
    timeout: Duration,
    expectedEvents: MutableList<ExpectedSessionEvent>,
    block: suspend () -> Unit,
) {
    val listening = CompletableDeferred<Unit>()
    val eventJob = sessionScope.launch {
        listening.complete(Unit)

        events.collect { event ->
            expectedEvents.removeAll { it.predicate(event) }

            if (expectedEvents.isEmpty())
                cancel()
        }
    }

    val blockJob = sessionScope.launch {
        listening.await()
        block()
    };

    { "missing expected events: ${expectedEvents.joinToString(", ") { it.description }}" }.asClue {
        withTimeoutOrNull(timeout) {
            joinAll(eventJob, blockJob)
        }.shouldNotBeNull()
    }
}