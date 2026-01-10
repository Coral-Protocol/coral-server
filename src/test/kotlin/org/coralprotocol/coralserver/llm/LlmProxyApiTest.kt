package org.coralprotocol.coralserver.llm

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.ListAgentRegistrySource
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.config.LlmEngineConfig
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.config.LlmProviderConfig
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.models.SessionIdentifier
import org.coralprotocol.coralserver.utils.dsl.registryAgent
import org.coralprotocol.coralserver.utils.dsl.sessionRequest
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds

class LlmProxyApiTest : CoralTest({
    test("testLlmProxyAndTelemetry").config(timeout = 30.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val application by inject<Application>()

        // 1. Setup Mock LLM Provider
        val mockApiKey = "mock-api-key"
        val mockResponse = buildJsonObject {
            put("id", "chatcmpl-123")
            putJsonArray("choices") {
                addJsonObject {
                    putJsonObject("message") {
                        put("role", "assistant")
                        put("content", "Hello from mock!")
                    }
                }
            }
        }

        application.routing {
            post("/mock-llm/chat/completions") {
                if (call.request.headers[HttpHeaders.Authorization] != "Bearer $mockApiKey") {
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                } else {
                    call.respond(HttpStatusCode.OK, mockResponse)
                }
            }
        }

        // 2. Configure LLM Proxy
        val testConfig = config.copy(
            llmProxyConfig = LlmProxyConfig(
                engines = mapOf("test-engine" to LlmEngineConfig("test-provider", "gpt-mock")),
                providers = mapOf("test-provider" to LlmProviderConfig(
                    baseUrl = "http://localhost/mock-llm",
                    apiKey = mockApiKey
                ))
            )
        )
        loadKoinModules(module { single { testConfig } })

        // 3. Setup Agent
        val agentName = "llm-agent"
        val agentIdentifier = RegistryAgentIdentifier(agentName, "1.0.0", AgentRegistrySourceIdentifier.Local)
        val agentReady = CompletableDeferred<Unit>()
        val testFinished = CompletableDeferred<Unit>()
        
        val registry by inject<AgentRegistry>()
        registry.sources.add(ListAgentRegistrySource(listOf(
            registryAgent(agentName) {
                runtime(FunctionRuntime { _, _ -> 
                    agentReady.complete(Unit)
                    testFinished.await()
                })
            }
        )))

        val sessionId = client.authenticatedPost(Sessions.WithNamespace(namespace = "test")) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(agentIdentifier) {}
                }
            })
        }.shouldBeOK().body<SessionIdentifier>()

        agentReady.await()

        val session = localSessionManager.getSessions("test").find { it.id == sessionId.sessionId }!!
        val agent = session.getAgent(agentName)
        val agentSecret = agent.secret

        // 4. Call LLM Proxy
        val proxyRequest = buildJsonObject {
            put("model", "test-engine")
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "Hello!")
                }
            }
        }

        val response = client.post("/llm-proxy/v1/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $agentSecret")
            contentType(ContentType.Application.Json)
            setBody(proxyRequest)
        }
        
        val responseBody = response.shouldBeOK().body<JsonObject>()

        responseBody["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content shouldBe "Hello from mock!"

        // 5. Verify Telemetry
        agent.telemetry.shouldHaveSize(1)
        val telemetry = agent.telemetry[0]
        telemetry.modelDescription shouldBe "gpt-mock"

        testFinished.complete(Unit)
        localSessionManager.waitAllSessions()
    }

    test("testLlmProxyEnvAndEngineRoute").config(timeout = 30.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val application by inject<Application>()

        // 1. Setup Mock LLM Provider
        val mockApiKey = "mock-api-key"
        val mockResponse = buildJsonObject {
            put("id", "chatcmpl-456")
            putJsonArray("choices") {
                addJsonObject {
                    putJsonObject("message") {
                        put("role", "assistant")
                        put("content", "Hello from engine mock!")
                    }
                }
            }
        }

        application.routing {
            post("/mock-llm-engine/chat/completions") {
                call.respond(HttpStatusCode.OK, mockResponse)
            }
        }

        // 2. Configure LLM Proxy
        val testConfig = config.copy(
            llmProxyConfig = LlmProxyConfig(
                engines = mapOf("custom-engine" to LlmEngineConfig("test-provider", "gpt-custom")),
                providers = mapOf("test-provider" to LlmProviderConfig(
                    baseUrl = "http://localhost/mock-llm-engine",
                    apiKey = mockApiKey
                ))
            )
        )
        loadKoinModules(module { single { testConfig } })

        // 3. Setup Agent with prescribed engineId
        val agentName = "env-agent"
        val agentIdentifier = RegistryAgentIdentifier(agentName, "1.0.0", AgentRegistrySourceIdentifier.Local)
        val envDeferred = CompletableDeferred<Map<String, String>>()
        val testFinished = CompletableDeferred<Unit>()

        val registry by inject<AgentRegistry>()
        registry.sources.add(ListAgentRegistrySource(listOf(
            registryAgent(agentName) {
                runtime(FunctionRuntime { executionContext, _ ->
                    envDeferred.complete(executionContext.buildEnvironment())
                    testFinished.await()
                })
            }
        )))

        val sessionId = client.authenticatedPost(Sessions.WithNamespace(namespace = "test-env")) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(agentIdentifier) {
                        // Prescribe engineId
                        engineId = "custom-engine"
                    }
                }
            })
        }.shouldBeOK().body<SessionIdentifier>()

        val env = envDeferred.await()

        // 4. Verify Environment Variables
        env["CORAL_LLM_URL"] shouldBe "http://localhost/llm-proxy/v1/chat/completions"
        env["CORAL_LLM_URL_WITH_ENGINE"] shouldBe "http://localhost/llm-proxy/v1/engines/custom-engine/chat/completions"

        val session = localSessionManager.getSessions("test-env").find { it.id == sessionId.sessionId }!!
        val agent = session.getAgent(agentName)
        val agentSecret = agent.secret

        // 5. Call LLM Proxy via engine-specific URL
        val proxyRequest = buildJsonObject {
            // Note: 'model' is not provided in body, should be picked up from URL
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", "Hello engine!")
                }
            }
        }

        val response = client.post("/llm-proxy/v1/engines/custom-engine/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $agentSecret")
            contentType(ContentType.Application.Json)
            setBody(proxyRequest)
        }

        val responseBody = response.shouldBeOK().body<JsonObject>()
        responseBody["choices"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content shouldBe "Hello from engine mock!"

        testFinished.complete(Unit)
        localSessionManager.waitAllSessions()
    }
})
