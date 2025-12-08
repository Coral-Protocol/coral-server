package org.coralprotocol.coralserver.session

import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.agent.graph.AgentGraphRequest
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentRequest
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryAgentInfo
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.routes.api.v1.BasicNamespace
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import kotlin.test.Test

class SessionApiTest : SessionBuildingE2E(
    listOf(
        RegistryAgent(
            info = RegistryAgentInfo(
                name = "delay",
                version = "1.0.0",
                description = "A test agent",
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
    )
) {

    @Test
    fun testCreateSession() = env {
        val sessionsRes = Sessions()
        val testNamespace = Sessions.WithNamespace(namespace = "test namespace")

        repeat(10) {
            val response = client.post(testNamespace) {
                contentType(ContentType.Application.Json)
                setBody(
                    AgentGraphRequest(
                        agents = listOf(
                            GraphAgentRequest(
                                id = AgentRegistryIdentifier("delay", "1.0.0"),
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

            assert(response.status == HttpStatusCode.OK)
        }

        var namespaces: List<BasicNamespace> = client.get(sessionsRes).body()
        assert(namespaces.size == 1)
        assert(namespaces.first().sessions.size == 10)

        val sessions: List<SessionId> = client.get(testNamespace).body()
        assert(sessions.size == 10)

        sessionManager.waitAllSessions()

        namespaces = client.get(sessionsRes).body()
        assert(namespaces.isEmpty())

        // namespace should be deleted when the last session exits
        assert(client.get(testNamespace).status == HttpStatusCode.NotFound)
    }

    @Test
    fun testDeleteSession() = env {
        val testNamespace = Sessions.WithNamespace(namespace = "test namespace")

        val sessionId: SessionIdentifier  = client.post(testNamespace) {
            contentType(ContentType.Application.Json)
            setBody(
                AgentGraphRequest(
                    agents = listOf(
                        GraphAgentRequest(
                            id = AgentRegistryIdentifier("delay", "1.0.0"),
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
        assert(client.delete(testSession).status == HttpStatusCode.OK)

        sessionManager.waitAllSessions()
    }
}