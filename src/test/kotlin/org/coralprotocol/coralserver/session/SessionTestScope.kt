package org.coralprotocol.coralserver.session

import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.asClue
import io.kotest.assertions.withClue
import io.kotest.core.test.TestScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.*
import org.coralprotocol.coralserver.config.*
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.routes.sse.v1.Mcp
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.server.coralServerModule
import org.coralprotocol.coralserver.util.mcpFunctionRuntime
import java.nio.file.Path
import java.util.*
import kotlin.time.Duration
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

private val logger = KotlinLogging.logger {}

class SessionTestScope(
    testScope: TestScope,
    val ktor: ApplicationTestBuilder,
    registryBuilder: AgentRegistrySourceBuilder.() -> Unit = {}
) {
    val authToken = UUID.randomUUID().toString()
    val config = Config(
        // port for testing is zero
        networkConfig = NetworkConfig(
            bindPort = 0u
        ),
        paymentConfig = PaymentConfig(
            wallets = listOf(
                Wallet.Solana(
                    name = "fake test wallet",
                    cluster = SolanaCluster.DEV_NET,
                    keypairPath = "fake-test-wallet.json",
                    walletAddress = "this is not a real wallet address"
                )
            ),
            remoteAgentWalletName = "fake test wallet"
        ),
        auth = AuthConfig(
            keys = setOf(authToken)
        )
    )
    val registry = AgentRegistry(config, registryBuilder)
    val applicationRuntimeContext = ApplicationRuntimeContext(config)
    val mcpToolManager = McpToolManager()
    val jupiterService = JupiterService()

    val sessionManager = LocalSessionManager(
        blockchainService = null,
        applicationRuntimeContext = applicationRuntimeContext,
        jupiterService = jupiterService,
        config = config,
        mcpToolManager = mcpToolManager,
        httpClient = ktor.client,
        managementScope = testScope,

        // if this is true, exceptions thrown (including assertions) in an agent's runtime will not exit a test
        // it also requires that session's coroutine scopes are canceled
        supervisedSessions = false,
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
                description = description,
                capabilities = setOf(),
                identifier = RegistryAgentIdentifier(name, version, AgentRegistrySourceIdentifier.Local)
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
        registryAgent.name to GraphAgent(
            registryAgent = registryAgent,
            name = registryAgent.name,
            description = registryAgent.description,
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

    suspend fun HttpClient.sseHandshake(secret: String) {
        this.sse(this.href(Mcp.Sse(agentSecret = secret))) {
            // We will get a session so long as the agent secret is valid, the following line makes sure a connection
            // was established on the server by waiting for one message
            incoming.take(1).collect {}
        }
    }

    fun HttpRequestBuilder.withAuthToken() {
        headers.append(HttpHeaders.Authorization, "Bearer $authToken")
    }

    suspend fun buildSession(agents: Map<UniqueAgentName, suspend (Client, LocalSession) -> Unit>) {
        val (session, _) = sessionManager.createSession(
            "test", AgentGraph(
                agents = agents.mapValues { (name, func) ->
                    graphAgent(
                        registryAgent = registryAgent(
                            name = name,
                            functionRuntime = ktor.client.mcpFunctionRuntime(name, "1.0.0", func)
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    ).second
                },
                customTools = mapOf(),
                groups = setOf(agents.keys.toSet())
            )
        )

        session.fullLifeCycle()
    }

    suspend fun graphToSession(graph: AgentGraph) =
        sessionManager.createSession("graphToSession", graph).first
}

suspend fun TestScope.sessionTest(
    registryBuilder: AgentRegistrySourceBuilder.(env: ApplicationTestBuilder) -> Unit = {},
    applicationBuilder: Application.(env: SessionTestScope) -> Unit = {},
    test: suspend SessionTestScope.() -> Unit
) {
    runTestApplication(coroutineContext) {
        client = createClient {
            install(Resources)
            install(WebSockets)
            install(SSE)
            install(HttpCookies)
            install(ClientContentNegotiation) {
                json(apiJsonConfig, contentType = ContentType.Application.Json)
            }
        }

        val sessionTestScope = SessionTestScope(this@sessionTest, this) {
            registryBuilder(this@runTestApplication)
        };

        application {
            coralServerModule(
                config = sessionTestScope.config,
                localSessionManager = sessionTestScope.sessionManager,
                registry = sessionTestScope.registry,
                x402Service = null,
                blockchainService = null
            )
            applicationBuilder(sessionTestScope)
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

suspend fun CoroutineScope.shouldPostEvents(
    timeout: Duration,
    expectedEvents: MutableList<ExpectedSessionEvent>,
    eventFlow: Flow<SessionEvent>,
    block: suspend () -> Unit,
) {
    val listening = CompletableDeferred<Unit>()
    val eventJob = launch {
        listening.complete(Unit)

        eventFlow.collect { event ->
            expectedEvents.removeAll { it.predicate(event) }

            if (expectedEvents.isEmpty())
                cancel()
        }
    }

    val blockJob = launch {
        listening.await()
        block()
    };

    { "missing expected events: ${expectedEvents.joinToString(", ") { it.description }}" }.asClue {
        withTimeoutOrNull(timeout) {
            joinAll(eventJob, blockJob)
        }.shouldNotBeNull()
    }
}

suspend fun LocalSession.shouldPostEvents(
    timeout: Duration,
    expectedEvents: MutableList<ExpectedSessionEvent>,
    block: suspend () -> Unit,
) {
    this.sessionScope.shouldPostEvents(timeout, expectedEvents, events.flow, block)
}

fun Iterable<SessionEvent>.shouldHaveEvents(expectedEvents: MutableList<ExpectedSessionEvent>) {
    this.forEach { event ->
        expectedEvents.removeAll { it.predicate(event) }
    }

    withClue("missing expected events: ${expectedEvents.joinToString(", ") { it.description }}") {
        expectedEvents.shouldBeEmpty()
    }
}