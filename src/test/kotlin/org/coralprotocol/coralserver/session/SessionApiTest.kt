package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.withClue
import io.kotest.inspectors.forAll
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.debug.ToolDebugAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentTool
import org.coralprotocol.coralserver.agent.graph.GraphAgentToolTransport
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.ListAgentRegistrySource
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.routes.RouteException
import org.coralprotocol.coralserver.routes.api.v1.BasicNamespace
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.session.models.SessionPersistenceMode
import org.coralprotocol.coralserver.session.reporting.SessionEndReport
import org.coralprotocol.coralserver.session.state.SessionState
import org.coralprotocol.coralserver.util.signatureVerifiedBody
import org.coralprotocol.coralserver.utils.dsl.SessionRuntimeSettingsBuilder
import org.coralprotocol.coralserver.utils.dsl.registryAgent
import org.coralprotocol.coralserver.utils.dsl.sessionRequest
import org.koin.core.component.inject
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SessionApiTest : CoralTest({
    val agentName = "delay"
    val agentVersion = "1.0.0"
    val agentIdentifier = RegistryAgentIdentifier(agentName, agentVersion, AgentRegistrySourceIdentifier.Local)

    suspend fun sessionWithDelay(
        client: HttpClient,
        delay: Long,
        cancel: Boolean,
        settings: SessionRuntimeSettingsBuilder.() -> Unit = {},
    ): SessionIdentifier {
        val registry by inject<AgentRegistry>()
        registry.sources.add(
            ListAgentRegistrySource(
                listOf(registryAgent(agentName) {
                    version = agentVersion
                    runtime(FunctionRuntime { executionContext, _ ->
                        val opt = executionContext.options["DELAY"]
                        val mustCancel = executionContext.options["MUST_CANCEL"]
                        if (opt is AgentOptionWithValue.Long)
                            delay(opt.value.value)

                        if (mustCancel is AgentOptionWithValue.Boolean && mustCancel.value.value)
                            throw AssertionError("Agent did not cancel")
                    })
                    option("DELAY", AgentOption.Long(default = 200))
                    option("MUST_CANCEL", AgentOption.Boolean(false))
                })
            )
        )

        val testNamespace = Sessions.WithNamespace(namespace = "test namespace")
        return client.authenticatedPost(testNamespace) {
            setBody(
                sessionRequest {
                    agentGraphRequest {
                        agent(agentIdentifier) {
                            option("DELAY", AgentOptionValue.Long(delay))
                            option("MUST_CANCEL", AgentOptionValue.Boolean(cancel))
                        }
                        isolateAllAgents()
                    }
                    runtimeSettings {
                        settings()
                    }
                }
            )
        }.shouldBeOK().body()
    }

    test("testCreateSession") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val sessionsRes = Sessions()
        val testNamespace = Sessions.WithNamespace(namespace = "test namespace")

        repeat(10) {
            sessionWithDelay(client, 100, false)
        }

        var namespaces: List<BasicNamespace> = shouldNotThrowAny {
            client.authenticatedGet(sessionsRes).body()
        }
        namespaces.shouldHaveSize(1)
        namespaces.first().sessions.shouldHaveSize(10)

        val sessions: List<SessionId> = client.authenticatedGet(testNamespace).body()
        sessions.shouldHaveSize(10)

        localSessionManager.waitAllSessions()

        namespaces = client.authenticatedGet(sessionsRes).body()
        namespaces.shouldBeEmpty()

        // namespace should be deleted when the last session exits
        client.authenticatedGet(testNamespace).shouldHaveStatus(HttpStatusCode.NotFound)
    }

    test("testDeleteSession") {
        val client by inject<HttpClient>()
        val sessionManager by inject<LocalSessionManager>()

        val testNamespace = Sessions.WithNamespace(namespace = "test namespace")
        val sessionId = sessionWithDelay(client, 1000, true)

        val testSession = Sessions.WithNamespace.Session(testNamespace, sessionId.sessionId)
        client.authenticatedDelete(testSession).shouldBeOK()

        sessionManager.waitAllSessions()
    }

    test("testSessionState") {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
        val threadCount = 5u
        val messageCount = 5u

        val sessionId: SessionIdentifier = client.authenticatedPost(namespace) {
            setBody(
                sessionRequest {
                    agentGraphRequest {
                        agent(SeedDebugAgent.identifier) {
                            option("SEED_THREAD_COUNT", AgentOptionValue.UInt(threadCount))
                            option("SEED_MESSAGE_COUNT", AgentOptionValue.UInt(messageCount))
                        }
                        isolateAllAgents()
                    }
                    runtimeSettings {
                        persistenceMode = SessionPersistenceMode.HoldAfterExit(1000)
                    }
                }
            )
        }.body()

        // Wait for agents' exit before checking the session state
        val session = localSessionManager.getSessions(sessionId.namespace).firstOrNull().shouldNotBeNull()
        session.joinAgents()

        val state: SessionState =
            client.authenticatedGet(Sessions.WithNamespace.Session(namespace, sessionId.sessionId))
                .shouldBeOK()
                .body()

        state.threads.shouldHaveSize(threadCount.toInt())
        state.threads.forAll {
            it.withMessageLock { messages ->
                messages.shouldHaveSize(messageCount.toInt())
            }
        }

        localSessionManager.waitAllSessions()
    }

    test("testSessionTtl").config(timeout = 10.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
        val threadCount = 5u
        val messageCount = 5u

        client.authenticatedPost(namespace) {
            setBody(
                sessionRequest {
                    agentGraphRequest {
                        agent(SeedDebugAgent.identifier) {
                            option("OPERATION_DELAY", AgentOptionValue.UInt(1000u)) // should take 25 seconds naturally
                            option("SEED_THREAD_COUNT", AgentOptionValue.UInt(threadCount))
                            option("SEED_MESSAGE_COUNT", AgentOptionValue.UInt(messageCount))
                        }
                        isolateAllAgents()
                    }
                    runtimeSettings {
                        ttl = 100.milliseconds
                    }
                }
            )
        }

        localSessionManager.waitAllSessions()
    }

    test("testSessionPersistence") {
        val client by inject<HttpClient>()

        var id = sessionWithDelay(
            client,
            550,
            false
        ) {
            persistenceMode = SessionPersistenceMode.HoldAfterExit(550)
        }
        continually(1.seconds) {
            client.authenticatedGet(
                Sessions.WithNamespace.Session(
                    Sessions.WithNamespace(namespace = id.namespace),
                    id.sessionId
                )
            ).shouldBeOK()
        }

        id = sessionWithDelay(
            client,
            100,
            false
        ) {
            persistenceMode = SessionPersistenceMode.MinimumTime(1100)
        }
        continually(1.seconds) {
            client.authenticatedGet(
                Sessions.WithNamespace.Session(
                    Sessions.WithNamespace(namespace = id.namespace),
                    id.sessionId
                )
            ).shouldBeOK()
        }

        id = sessionWithDelay(
            client,
            50,
            false,
        ) {
            persistenceMode = SessionPersistenceMode.None
        }
        delay(100)

        client.authenticatedGet(
            Sessions.WithNamespace.Session(
                Sessions.WithNamespace(namespace = id.namespace),
                id.sessionId
            )
        ).shouldHaveStatus(HttpStatusCode.NotFound)

        id = sessionWithDelay(
            client,
            50,
            false,
        ) {
            persistenceMode = SessionPersistenceMode.HoldAfterExit(1000)
        }
        delay(100)

        client.authenticatedGet(
            Sessions.WithNamespace.Session(
                Sessions.WithNamespace(namespace = id.namespace),
                id.sessionId
            )
        ).shouldBeOK().body<SessionState>().closing.shouldBeTrue()
    }

    test("testSessionWebhook").config(timeout = 30.seconds) {
        val client by inject<HttpClient>()
        val application by inject<Application>()
        val config by inject<NetworkConfig>()
        val json by inject<Json>()

        val path = "webhook"

        val completableDeferred = CompletableDeferred<SessionEndReport?>()

        application.routing {
            post(path) {
                try {
                    completableDeferred.complete(signatureVerifiedBody(json, config.webhookSecret))
                    throw RouteException(HttpStatusCode.Unauthorized)
                } finally {
                    completableDeferred.complete(null)
                }
            }
        }

        val id = sessionWithDelay(
            client,
            100,
            false
        ) {
            webhooks {
                sessionEndUrl(path)
            }
        }

        val report = withClue("invalid signature") {
            completableDeferred.await().shouldNotBeNull()
        }

        report.sessionId.shouldBeEqual(id.sessionId)
        report.namespace.shouldBeEqual(id.namespace)
        report.agentStats.shouldHaveSingleElement {
            it.name == agentName
        }
    }

    test("testCustomTools").config(timeout = 30.seconds) {
        val client by inject<HttpClient>()
        val application by inject<Application>()
        val config by inject<NetworkConfig>()
        val json by inject<Json>()
        val localSessionManager by inject<LocalSessionManager>()

        val toolUrl = "customTool"
        val toolName = "test"

        @Serializable
        data class ToolPayload(
            val a: String = UUID.randomUUID().toString(),
            val b: String = UUID.randomUUID().toString(),
            val c: String = UUID.randomUUID().toString(),
        )

        val toolPayload = ToolPayload()
        val deferredPayload = CompletableDeferred<Any>()

        @Serializable
        @Resource("customTool/{sessionId}/{agentId}")
        class CustomToolPath(val sessionId: String, val agentId: String)
        application.routing {
            post<CustomToolPath> { _ ->
                try {
                    deferredPayload.complete(
                        signatureVerifiedBody<ToolPayload>(json, config.customToolSecret).shouldBeEqual(toolPayload)
                    )
                    call.respond(HttpStatusCode.OK, "hello world")
                } catch (e: Exception) {
                    deferredPayload.complete(e)
                }
            }
        }

        client.authenticatedPost(Sessions.WithNamespace(namespace = "test namespace")) {
            setBody(
                sessionRequest {
                    agentGraphRequest {
                        agent(ToolDebugAgent.identifier) {
                            provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                            option("TOOL_NAME", AgentOptionValue.String(toolName))
                            option("TOOL_INPUT", AgentOptionValue.String(json.encodeToString(toolPayload)))
                            toolAccess(toolName)
                        }
                        tool(
                            toolName, GraphAgentTool(
                                transport = GraphAgentToolTransport.Http(
                                    url = toolUrl,
                                ),
                                schema = Tool(
                                    name = toolName,
                                    description = "A tool in a unit test",
                                    inputSchema = Tool.Input(), // no verification is done on this
                                    outputSchema = null,
                                    annotations = null,
                                )
                            )
                        )
                        isolateAllAgents()
                    }
                }
            )
        }

        localSessionManager.waitAllSessions()

        val response = deferredPayload.await()
        if (response is Exception)
            throw response

        response.shouldBeInstanceOf<ToolPayload>().shouldBeEqual(toolPayload)
    }
})