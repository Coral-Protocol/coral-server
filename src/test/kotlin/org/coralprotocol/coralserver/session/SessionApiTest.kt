package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.debug.ToolDebugAgent
import org.coralprotocol.coralserver.agent.graph.*
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.routes.api.v1.BasicNamespace
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.server.RouteException
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.models.*
import org.coralprotocol.coralserver.session.reporting.SessionEndReport
import org.coralprotocol.coralserver.session.state.SessionState
import org.coralprotocol.coralserver.util.signatureVerifiedBody
import java.util.*
import kotlin.time.Duration.Companion.seconds

class SessionApiTest : FunSpec({
    val agentName = "delay"
    val agentVersion = "1.0.0"
    val agentIdentifier = RegistryAgentIdentifier(agentName, agentVersion, AgentRegistrySourceIdentifier.Local)
    val registryBuilder: AgentRegistrySourceBuilder.(ApplicationTestBuilder) -> Unit = {
        this.addLocalAgents(
            listOf(
                RegistryAgent(
                    info = RegistryAgentInfo(
                        description = "test agent",
                        capabilities = setOf(),
                        identifier = agentIdentifier
                    ),
                    runtimes = LocalAgentRuntimes(
                        functionRuntime = FunctionRuntime { executionContext, _ ->
                            val opt = executionContext.options["DELAY"]
                            val mustCancel = executionContext.options["MUST_CANCEL"]
                            if (opt is AgentOptionWithValue.Long)
                                delay(opt.value.value)

                            if (mustCancel is AgentOptionWithValue.Boolean && mustCancel.value.value)
                                throw AssertionError("Agent did not cancel")
                        },
                    ),
                    options = mapOf(
                        "DELAY" to AgentOption.Long(default = 200),
                        "MUST_CANCEL" to AgentOption.Boolean(false)
                    ),
                )
            ), "test agent batch"
        )
    }

    suspend fun SessionTestScope.sessionWithDelay(
        delay: Long,
        settings: SessionRuntimeSettings = SessionRuntimeSettings()
    ): SessionIdentifier {
        val testNamespace = Sessions.WithNamespace(namespace = "test namespace")
        val response = ktor.client.post(testNamespace) {
            withAuthToken()
            contentType(ContentType.Application.Json)
            setBody(
                SessionRequest(
                    agentGraphRequest = AgentGraphRequest(
                        agents = listOf(
                            GraphAgentRequest(
                                id = agentIdentifier,
                                name = "delay",
                                description = "",
                                provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                options = mapOf("DELAY" to AgentOptionValue.Long(delay))
                            )
                        ),
                        groups = setOf(setOf("delay")),
                    ),
                    sessionRuntimeSettings = settings
                )
            )
        }

        response.shouldHaveStatus(HttpStatusCode.OK)
        return response.body<SessionIdentifier>()
    }

    test("testCreateSession") {
        sessionTest(registryBuilder) {
            val sessionsRes = Sessions()
            val testNamespace = Sessions.WithNamespace(namespace = "test namespace")

            repeat(10) {
                sessionWithDelay(100)
            }

            var namespaces: List<BasicNamespace> = shouldNotThrowAny {
                ktor.client.get(sessionsRes) {
                    withAuthToken()
                }.body()
            }
            namespaces.shouldHaveSize(1)
            namespaces.first().sessions.shouldHaveSize(10)

            val sessions: List<SessionId> = ktor.client.get(testNamespace) {
                withAuthToken()
            }.body()
            sessions.shouldHaveSize(10)

            sessionManager.waitAllSessions()

            namespaces = ktor.client.get(sessionsRes) {
                withAuthToken()
            }.body()
            assert(namespaces.isEmpty())

            // namespace should be deleted when the last session exits
            ktor.client.get(testNamespace) {
                withAuthToken()
            }.shouldHaveStatus(HttpStatusCode.NotFound)
        }
    }

    test("testDeleteSession") {
        sessionTest(registryBuilder) {
            val testNamespace = Sessions.WithNamespace(namespace = "test namespace")

            val sessionId: SessionIdentifier = ktor.client.post(testNamespace) {
                withAuthToken()
                contentType(ContentType.Application.Json)
                setBody(
                    SessionRequest(
                        agentGraphRequest = AgentGraphRequest(
                            agents = listOf(
                                GraphAgentRequest(
                                    id = agentIdentifier,
                                    name = "delay",
                                    description = "",
                                    provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                    options = mapOf(
                                        "DELAY" to AgentOptionValue.Long(1000),
                                        "MUST_CANCEL" to AgentOptionValue.Boolean(true)
                                    )
                                )
                            ),
                            groups = setOf(setOf("delay")),
                        )
                    )
                )
            }.body()

            val testSession = Sessions.WithNamespace.Session(testNamespace, sessionId.sessionId)
            ktor.client.delete(testSession) {
                withAuthToken()
            }.shouldHaveStatus(HttpStatusCode.OK)

            sessionManager.waitAllSessions()
        }
    }

    test("testSessionState") {
        sessionTest({
            addLocalAgents(
                listOf(
                    SeedDebugAgent(it.client).generate(),
                ), "debug agents"
            )
        }) {
            val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
            val threadCount = 5u
            val messageCount = 5u

            val sessionId: SessionIdentifier = ktor.client.post(namespace) {
                withAuthToken()
                contentType(ContentType.Application.Json)
                setBody(
                    SessionRequest(
                        agentGraphRequest = AgentGraphRequest(
                            agents = listOf(
                                GraphAgentRequest(
                                    id = SeedDebugAgent.identifier,
                                    name = "seed",
                                    description = "",
                                    provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                    options = mapOf(
                                        "SEED_THREAD_COUNT" to AgentOptionValue.UInt(threadCount),
                                        "SEED_MESSAGE_COUNT" to AgentOptionValue.UInt(messageCount),
                                    )
                                )
                            ),
                            groups = setOf(setOf("seed")),
                        ),
                        sessionRuntimeSettings = SessionRuntimeSettings(
                            persistenceMode = SessionPersistenceMode.HoldAfterExit(1000)
                        )
                    )
                )
            }.body()

            // Wait for agents' exit before checking the session state
            val session = sessionManager.getSessions(sessionId.namespace).firstOrNull().shouldNotBeNull()
            session.joinAgents()

            val state: SessionState =
                ktor.client.get(Sessions.WithNamespace.Session(namespace, sessionId.sessionId)) {
                    withAuthToken()
                }.body()
            state.threads.shouldHaveSize(threadCount.toInt())
            state.threads.forAll {
                it.withMessageLock { messages ->
                    messages.shouldHaveSize(messageCount.toInt())
                }
            }

            sessionManager.waitAllSessions()
        }
    }

    test("testSessionTtl").config(timeout = 10.seconds) {
        sessionTest({
            addLocalAgents(
                listOf(
                    SeedDebugAgent(it.client).generate(),
                ), "debug agents"
            )
        }) {
            val namespace = Sessions.WithNamespace(namespace = "debug agent namespace")
            val threadCount = 5u
            val messageCount = 5u

            ktor.client.post(namespace) {
                withAuthToken()
                contentType(ContentType.Application.Json)
                setBody(
                    SessionRequest(
                        agentGraphRequest = AgentGraphRequest(
                            agents = listOf(
                                GraphAgentRequest(
                                    id = SeedDebugAgent.identifier,
                                    name = "seed",
                                    description = "",
                                    provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                    options = mapOf(
                                        "OPERATION_DELAY" to AgentOptionValue.UInt(1000u), // should take 25 seconds naturally
                                        "SEED_THREAD_COUNT" to AgentOptionValue.UInt(threadCount),
                                        "SEED_MESSAGE_COUNT" to AgentOptionValue.UInt(messageCount),
                                    )
                                )
                            ),
                            groups = setOf(setOf("seed")),
                        ),
                        sessionRuntimeSettings = SessionRuntimeSettings(
                            ttl = 100 // should die before test timeout
                        )
                    )
                )
            }

            sessionManager.waitAllSessions()
        }
    }

    test("testSessionPersistence") {
        sessionTest(registryBuilder) {
            var id = sessionWithDelay(
                550,
                SessionRuntimeSettings(persistenceMode = SessionPersistenceMode.HoldAfterExit(550))
            )
            continually(1.seconds) {
                ktor.client.get(
                    Sessions.WithNamespace.Session(
                        Sessions.WithNamespace(namespace = id.namespace),
                        id.sessionId
                    )
                ) {
                    withAuthToken()
                }.shouldHaveStatus(HttpStatusCode.OK)
            }

            id = sessionWithDelay(
                100,
                SessionRuntimeSettings(persistenceMode = SessionPersistenceMode.MinimumTime(1100))
            )
            continually(1.seconds) {
                ktor.client.get(
                    Sessions.WithNamespace.Session(
                        Sessions.WithNamespace(namespace = id.namespace),
                        id.sessionId
                    )
                ) {
                    withAuthToken()
                }
            }

            id = sessionWithDelay(
                50,
                SessionRuntimeSettings(persistenceMode = SessionPersistenceMode.None)
            )
            delay(100)
            ktor.client.get(
                Sessions.WithNamespace.Session(
                    Sessions.WithNamespace(namespace = id.namespace),
                    id.sessionId
                )
            ) {
                withAuthToken()
            }.shouldHaveStatus(HttpStatusCode.NotFound)

            id = sessionWithDelay(
                50,
                SessionRuntimeSettings(persistenceMode = SessionPersistenceMode.HoldAfterExit(1000))
            )
            delay(100)

            val closing = ktor.client.get(
                Sessions.WithNamespace.Session(
                    Sessions.WithNamespace(namespace = id.namespace),
                    id.sessionId
                )
            ) {
                withAuthToken()
            }

            closing.shouldHaveStatus(HttpStatusCode.OK)
            closing.body<SessionState>().closing.shouldBeTrue()
        }
    }

    test("testSessionWebhook").config(timeout = 30.seconds) {
        val completableDeferred = CompletableDeferred<SessionEndReport?>()

        sessionTest(registryBuilder, applicationBuilder = {
            routingRoot.post("webhook") {
                try {
                    completableDeferred.complete(signatureVerifiedBody(it.config.networkConfig.webhookSecret))
                    throw RouteException(HttpStatusCode.Unauthorized)
                } finally {
                    completableDeferred.complete(null)
                }
            }
        }) {
            val id = sessionWithDelay(
                100,
                SessionRuntimeSettings(
                    webhooks = SessionWebhooks(
                        sessionEnd = SessionEndWebhook("webhook")
                    )
                )
            )

            val report = withClue("invalid signature") {
                completableDeferred.await().shouldNotBeNull()
            }

            report.sessionId.shouldBeEqual(id.sessionId)
            report.namespace.shouldBeEqual(id.namespace)
            report.agentStats.shouldHaveSingleElement {
                it.name == agentName
            }
        }
    }

    test("testCustomTools") {
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

        sessionTest(
            registryBuilder = {
                addLocalAgents(
                    listOf(
                        ToolDebugAgent(it.client).generate(),
                    ), "debug agents"
                )
            },
            applicationBuilder = {
                routingRoot.post(toolUrl) {
                    try {
                        deferredPayload.complete(
                            signatureVerifiedBody<ToolPayload>(it.config.networkConfig.customToolSecret).shouldBeEqual(
                                toolPayload
                            )
                        )
                        call.respond(HttpStatusCode.OK, "hello world")
                    } catch (e: Exception) {
                        deferredPayload.complete(e)
                    }
                }
            }) {


            ktor.client.post(Sessions.WithNamespace(namespace = "test namespace")) {
                withAuthToken()
                contentType(ContentType.Application.Json)
                setBody(
                    SessionRequest(
                        agentGraphRequest = AgentGraphRequest(
                            agents = listOf(
                                GraphAgentRequest(
                                    id = ToolDebugAgent.identifier,
                                    name = "tool",
                                    description = "",
                                    provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                    options = mapOf(
                                        "TOOL_NAME" to AgentOptionValue.String(toolName),
                                        "TOOL_INPUT" to AgentOptionValue.String(
                                            apiJsonConfig.encodeToString(toolPayload)
                                        ),
                                    ),
                                    customToolAccess = setOf(toolName)
                                )
                            ),
                            groups = setOf(setOf("tool")),
                            customTools = mapOf(
                                toolName to GraphAgentTool(
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
                        )
                    )
                )
            }

            sessionManager.waitAllSessions()

            val response = deferredPayload.await()
            if (response is Exception)
                throw response

            response.shouldBeInstanceOf<ToolPayload>().shouldBeEqual(toolPayload)
        }
    }
})