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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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
import org.coralprotocol.coralserver.modules.ktor.coralServerModule
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
    val unitTestSecret = UUID.randomUUID().toString()
    val config = RootConfig(
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
        authConfig = AuthConfig(
            keys = setOf(authToken)
        ),
        debugConfig = DebugConfig(
            additionalDockerEnvironment = mapOf("UNIT_TEST_SECRET" to unitTestSecret),
            additionalExecutableEnvironment = mapOf("UNIT_TEST_SECRET" to unitTestSecret)
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
            customTools = mapOf(),
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

data class TestEvent<Event>(
    val description: String,
    val predicate: (event: Event) -> Boolean,
)

suspend fun <Event, FlowType, R> CoroutineScope.shouldPostEvents(
    timeout: Duration,
    allowUnexpectedEvents: Boolean = false,
    events: MutableList<TestEvent<Event>> = mutableListOf(),
    eventFlow: FlowType,
    block: suspend (FlowType) -> R,
): R where FlowType : Flow<Event> {
    val listening = CompletableDeferred<Unit>()
    val eventJob = launch {
        listening.complete(Unit)

        eventFlow.collect { event ->
            if (!events.removeAll { it.predicate(event) } && !allowUnexpectedEvents)
                throw AssertionError("Unexpected event: $event")

            if (events.isEmpty())
                cancel()
        }
    }

    val retVal = CompletableDeferred<R>()
    val blockJob = launch {
        listening.await()
        retVal.complete(block(eventFlow))
    };

    {
        "missing expected events: ${events.joinToString(", ") { it.description }}"
    }.asClue {
        withTimeoutOrNull(timeout) {
            joinAll(eventJob, blockJob)
        }.shouldNotBeNull()
    }

    return retVal.await()
}

suspend fun <R> LocalSession.shouldPostEvents(
    timeout: Duration,
    allowUnexpectedEvents: Boolean = false,
    events: MutableList<TestEvent<SessionEvent>>,
    block: suspend (SharedFlow<SessionEvent>) -> R,
): R =
    this.sessionScope.shouldPostEvents(timeout, allowUnexpectedEvents, events, this@shouldPostEvents.events.flow, block)

suspend fun <Event, R> CoroutineScope.shouldPostEventsFromBody(
    timeout: Duration,
    allowUnexpectedEvents: Boolean = false,
    events: MutableList<TestEvent<Event>>,
    block: suspend (MutableSharedFlow<Event>) -> R,
): R {
    val flow = MutableSharedFlow<Event>()
    return shouldPostEvents(timeout, allowUnexpectedEvents, events, flow, block)
}

fun <Event> Iterable<Event>.shouldHaveEvents(events: MutableList<TestEvent<Event>>) {
    this.forEach { event ->
        events.removeAll { it.predicate(event) }
    }

    withClue("missing expected events: ${events.joinToString(", ") { it.description }}") {
        events.shouldBeEmpty()
    }
}