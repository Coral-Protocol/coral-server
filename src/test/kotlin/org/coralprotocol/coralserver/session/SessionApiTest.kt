package org.coralprotocol.coralserver.session

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.routes.api.v1.BasicNamespace
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.models.SessionIdentifier

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

    test("testCreateSession") {
        sessionTest(registryBuilder) {
            val sessionsRes = Sessions()
            val testNamespace = Sessions.WithNamespace(namespace = "test namespace")

            repeat(10) {
                val response = ktor.client.post(testNamespace) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        AgentGraphRequest(
                            agents = listOf(
                                GraphAgentRequest(
                                    id = agentIdentifier,
                                    name = "delay",
                                    description = "",
                                    provider = GraphAgentProvider.Local(RuntimeId.FUNCTION),
                                    options = mapOf("DELAY" to AgentOptionValue.Long(100))
                                )
                            ),
                            groups = setOf(setOf("delay")),
                        )
                    )
                }

                response.shouldHaveStatus(HttpStatusCode.OK)
            }

            var namespaces: List<BasicNamespace> = shouldNotThrowAny {
                ktor.client.get(sessionsRes).body()
            }
            namespaces.shouldHaveSize(1)
            namespaces.first().sessions.shouldHaveSize(10)

            val sessions: List<SessionId> = ktor.client.get(testNamespace).body()
            sessions.shouldHaveSize(10)

            sessionManager.waitAllSessions()

            namespaces = ktor.client.get(sessionsRes).body()
            assert(namespaces.isEmpty())

            // namespace should be deleted when the last session exits
            ktor.client.get(testNamespace).shouldHaveStatus(HttpStatusCode.NotFound)
        }
    }

    test("testDeleteSession") {
        sessionTest(registryBuilder) {
            val testNamespace = Sessions.WithNamespace(namespace = "test namespace")

            val sessionId: SessionIdentifier = ktor.client.post(testNamespace) {
                contentType(ContentType.Application.Json)
                setBody(
                    AgentGraphRequest(
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
            }.body()

            val testSession = Sessions.WithNamespace.Session(testNamespace, sessionId.sessionId)
            ktor.client.delete(testSession).shouldHaveStatus(HttpStatusCode.OK)

            sessionManager.waitAllSessions()
        }
    }
})