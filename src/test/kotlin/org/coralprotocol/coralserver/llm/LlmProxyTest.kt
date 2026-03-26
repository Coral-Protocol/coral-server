package org.coralprotocol.coralserver.llm

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.debug.PuppetDebugAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeApiUrl
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeModelProvider
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeString
import org.coralprotocol.coralserver.config.LlmProxyConfig
import org.coralprotocol.coralserver.config.LlmProxyProviderConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.routes.api.v1.LocalSessions
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionIdentifier
import org.coralprotocol.coralserver.utils.dsl.sessionRequest
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest
import java.util.UUID
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.koin.test.inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val MOCK_OPENAI_RESPONSE = """{
    "id":"chatcmpl-test","object":"chat.completion","model":"gpt-test",
    "choices":[{"index":0,"message":{"role":"assistant","content":"Hello from upstream"},"finish_reason":"stop"}],
    "usage":{"prompt_tokens":15,"completion_tokens":5,"total_tokens":20}
}"""

private const val MOCK_OPENAI_STREAM = """data: {"id":"chatcmpl-test","object":"chat.completion.chunk","model":"gpt-test","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}

data: {"id":"chatcmpl-test","object":"chat.completion.chunk","model":"gpt-test","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}

data: {"id":"chatcmpl-test","object":"chat.completion.chunk","model":"gpt-test","choices":[{"index":0,"delta":{"content":" from"},"finish_reason":null}]}

data: {"id":"chatcmpl-test","object":"chat.completion.chunk","model":"gpt-test","choices":[{"index":0,"delta":{"content":" upstream"},"finish_reason":"stop"}],"usage":{"prompt_tokens":15,"completion_tokens":3,"total_tokens":18}}

data: [DONE]

"""

class LlmProxyTest : CoralTest({

    fun enableMockProxy() {
        loadKoinModules(module {
            single<LlmProxyConfig> {
                LlmProxyConfig(
                    enabled = true,
                    providers = mapOf("mock" to LlmProxyProviderConfig(apiKey = "mock-key"))
                )
            }
        })
    }

    fun enableProxyWithUpstream(providerName: String, apiKey: String, baseUrl: String) {
        loadKoinModules(module {
            single<LlmProxyConfig> {
                LlmProxyConfig(
                    enabled = true,
                    providers = mapOf(providerName to LlmProxyProviderConfig(apiKey = apiKey, baseUrl = baseUrl))
                )
            }
        })
    }

    suspend fun createPuppetSession(client: HttpClient, localSessionManager: LocalSessionManager): Pair<String, LocalSession> {
        val id: SessionIdentifier = client.authenticatedPost(LocalSessions.Session()) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(PuppetDebugAgent.identifier) {
                        name = "test-agent"
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                    isolateAllAgents()
                }
            })
        }.body()

        val session = localSessionManager.getSessions(id.namespace).first()
        val secret = session.agents["test-agent"]!!.secret
        return secret to session
    }

    // ─── Mock provider tests (routing & config validation) ───────────────────────

    test("mock provider returns buffered response").config(invocationTimeout = 10.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        enableMockProxy()

        val (secret, _) = createPuppetSession(client, localSessionManager)

        val response = client.post("/llm-proxy/$secret/mock/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody("""{"model":"test-model","messages":[{"role":"user","content":"hi"}]}""")
        }

        response.status shouldBe HttpStatusCode.OK
        response.headers["x-coral-mock"] shouldBe "true"

        val body = response.bodyAsText()
        val json = Json.decodeFromString<JsonObject>(body)
        json["model"]?.jsonPrimitive?.content shouldBe "test-model"
        json["choices"].shouldNotBeNull()
        json["usage"].shouldNotBeNull()
    }

    test("mock provider returns streaming response").config(invocationTimeout = 10.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        enableMockProxy()

        val (secret, _) = createPuppetSession(client, localSessionManager)

        val response = client.post("/llm-proxy/$secret/mock/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody("""{"model":"test-model","messages":[{"role":"user","content":"hi"}],"stream":true}""")
        }

        response.status shouldBe HttpStatusCode.OK
        response.headers["x-coral-mock"] shouldBe "true"

        val body = response.bodyAsText()
        body shouldContain "data: "
        body shouldContain "[DONE]"
        body shouldContain "Hello"
        body shouldContain "mock"
    }

    test("invalid agent secret returns 401").config(invocationTimeout = 10.seconds) {
        val client by inject<HttpClient>()
        enableMockProxy()

        val response = client.post("/llm-proxy/invalid-secret/mock/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody("""{"model":"test","messages":[]}""")
        }

        response.status shouldBe HttpStatusCode.Unauthorized
    }

    test("unknown provider returns 400").config(invocationTimeout = 10.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        enableMockProxy()

        val (secret, _) = createPuppetSession(client, localSessionManager)

        val response = client.post("/llm-proxy/$secret/nonexistent/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody("""{"model":"test","messages":[]}""")
        }

        response.status shouldBe HttpStatusCode.BadRequest
    }

    test("disabled proxy returns 503").config(invocationTimeout = 10.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val (secret, _) = createPuppetSession(client, localSessionManager)

        val response = client.post("/llm-proxy/$secret/mock/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody("""{"model":"test","messages":[]}""")
        }

        response.status shouldBe HttpStatusCode.ServiceUnavailable
    }

    test("telemetry event emitted on mock request").config(invocationTimeout = 10.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        enableMockProxy()

        val (secret, session) = createPuppetSession(client, localSessionManager)

        val eventDeferred = async {
            withTimeout(5.seconds) {
                session.events.first { it is SessionEvent.LlmProxyCall }
            }
        }

        client.post("/llm-proxy/$secret/mock/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody("""{"model":"gpt-test","messages":[{"role":"user","content":"hi"}]}""")
        }

        val event = eventDeferred.await() as SessionEvent.LlmProxyCall
        event.provider shouldBe "mock"
        event.model shouldBe "gpt-test"
        event.success shouldBe true
        event.streaming shouldBe false
        event.inputTokens shouldBe 10
        event.outputTokens shouldBe 3
        event.errorKind shouldBe null
    }

    test("unconfigured provider returns 400").config(invocationTimeout = 10.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        enableMockProxy()

        val (secret, _) = createPuppetSession(client, localSessionManager)

        val response = client.post("/llm-proxy/$secret/openai/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody("""{"model":"gpt-4","messages":[]}""")
        }

        response.status shouldBe HttpStatusCode.BadRequest
        response.bodyAsText() shouldContain "No API key configured"
    }

    // ─── Real proxy forwarding tests (mock upstream server) ──────────────────────

    test("proxy forwards buffered request to upstream and extracts tokens").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val application by inject<Application>()

        val upstreamPath = "/mock-upstream-${UUID.randomUUID()}"
        val capturedHeaders = CompletableDeferred<Map<String, List<String>>>()

        application.routing {
            post("$upstreamPath/v1/chat/completions") {
                capturedHeaders.complete(
                    call.request.headers.entries().associate { (k, v) -> k.lowercase() to v }
                )
                call.respondText(MOCK_OPENAI_RESPONSE, ContentType.Application.Json)
            }
        }

        enableProxyWithUpstream("openai", "sk-test-key-123", upstreamPath)

        val (secret, session) = createPuppetSession(client, localSessionManager)

        try {
            val eventDeferred = async {
                withTimeout(5.seconds) {
                    session.events.first { it is SessionEvent.LlmProxyCall }
                }
            }

            val response = client.post("/llm-proxy/$secret/openai/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer should-be-stripped")
                setBody("""{"model":"gpt-test","messages":[{"role":"user","content":"hi"}]}""")
            }

            response.status shouldBe HttpStatusCode.OK

            val body = response.bodyAsText()
            body shouldContain "Hello from upstream"

            // Verify headers: auth was swapped
            val headers = capturedHeaders.await()
            headers["authorization"]?.first() shouldBe "Bearer sk-test-key-123"

            // Verify telemetry with token extraction
            val event = eventDeferred.await() as SessionEvent.LlmProxyCall
            event.provider shouldBe "openai"
            event.model shouldBe "gpt-test"
            event.success shouldBe true
            event.streaming shouldBe false
            event.inputTokens shouldBe 15
            event.outputTokens shouldBe 5
        } finally {
            session.cancelAndJoinAgents()
        }
    }

    // Note: streaming forwarding cannot be fully tested with Ktor's in-process test engine
    // because it doesn't simulate real TCP streaming channels. The streaming path is tested
    // E2E with real OpenAI (gated on CORAL_TEST_OPENAI_API_KEY) below.

    test("proxy forwards upstream error and classifies it").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val application by inject<Application>()

        val upstreamPath = "/mock-upstream-error-${UUID.randomUUID()}"

        application.routing {
            post("$upstreamPath/v1/chat/completions") {
                call.respondText(
                    """{"error":{"message":"Rate limit exceeded","type":"rate_limit_error"}}""",
                    ContentType.Application.Json,
                    HttpStatusCode.TooManyRequests
                )
            }
        }

        enableProxyWithUpstream("openai", "sk-test-key", upstreamPath)

        val (secret, session) = createPuppetSession(client, localSessionManager)

        try {
            val response = client.post("/llm-proxy/$secret/openai/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                setBody("""{"model":"gpt-4","messages":[{"role":"user","content":"hi"}]}""")
            }

            response.status shouldBe HttpStatusCode.TooManyRequests
            response.bodyAsText() shouldContain "Rate limit exceeded"
        } finally {
            session.cancelAndJoinAgents()
        }
    }

    // ─── E2E: prototype runtime through proxy to real OpenAI ─────────────────────

    val openaiApiKey = System.getenv("CORAL_TEST_OPENAI_API_KEY")

    test("e2e proxy with real openai").config(
        enabled = openaiApiKey != null,
        invocationTimeout = 1.minutes
    ) {
        loadKoinModules(module {
            single<LlmProxyConfig> {
                LlmProxyConfig(
                    enabled = true,
                    providers = mapOf("openai" to LlmProxyProviderConfig(apiKey = openaiApiKey!!))
                )
            }
        })

        multiAgentPayloadTest(
            PrototypeModelProvider.OpenAI(
                PrototypeString.Inline("proxy-managed"),
                PrototypeString.Inline("gpt-4.1-nano"),
                url = PrototypeApiUrl.Proxy
            )
        )
    }
})
