package org.coralprotocol.coralserver.llmproxy

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
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
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.koin.test.inject
import java.util.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val totalTokens = 20L
private const val outputTokens = 5L
private const val inputTokens = 15L;


private const val MOCK_OPENAI_RESPONSE = """{
    "id":"chatcmpl-test","object":"chat.completion","model":"gpt-test",
    "choices":[{"index":0,"message":{"role":"assistant","content":"Hello from upstream"},"finish_reason":"stop"}],
    "usage":{"prompt_tokens":$inputTokens,"completion_tokens":$outputTokens,"total_tokens":$totalTokens}
}"""

class LlmProxyTest : CoralTest({

    suspend fun withProxySession(
        providerName: String,
        apiKey: String,
        baseUrl: String,
        block: suspend (secret: String, session: LocalSession) -> Unit
    ) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        loadKoinModules(module {
            single<LlmProxyConfig> {
                LlmProxyConfig(
                    enabled = true,
                    providers = mapOf(providerName to LlmProxyProviderConfig(apiKey = apiKey, baseUrl = baseUrl))
                )
            }
        })

        val agentName = "test-agent"
        val id: SessionIdentifier = client.authenticatedPost(LocalSessions.Session()) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(PuppetDebugAgent.identifier) {
                        name = agentName
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                    isolateAllAgents()
                }
            })
        }.body()

        val session = localSessionManager.getSessions(id.namespace).first()
        val secret = session.agents[agentName].shouldNotBeNull().secret

        try {
            block(secret, session)
        } finally {
            session.cancelAndJoinAgents()
        }
    }

    test("proxyForwardsBufferedRequestToUpstreamAndExtractsTokens").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()
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

        val key = "sk-test-key-${UUID.randomUUID()}"
        withProxySession("openai", key, upstreamPath) { secret, session ->
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

            response.shouldBeOK()
            response.bodyAsText().shouldContain("Hello from upstream")

            val headers = capturedHeaders.await()
            headers["authorization"].shouldNotBeNull().shouldContainExactly("Bearer $key")

            val event = eventDeferred.await() as SessionEvent.LlmProxyCall
            event.provider.shouldBeEqual("openai")
            event.model.shouldNotBeNull().shouldBeEqual("gpt-test")
            event.success.shouldBeTrue()
            event.streaming.shouldBeFalse()
            event.inputTokens.shouldNotBeNull().shouldBeEqual(inputTokens)
            event.outputTokens.shouldNotBeNull().shouldBeEqual(outputTokens)
        }
    }

    test("anthropicXApiKeyPassThrough").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()
        val application by inject<Application>()

        val upstreamPath = "/mock-upstream-anthropic-${UUID.randomUUID()}"
        val capturedHeaders = CompletableDeferred<Map<String, List<String>>>()

        application.routing {
            post("$upstreamPath/v1/messages") {
                capturedHeaders.complete(
                    call.request.headers.entries().associate { (k, v) -> k.lowercase() to v }
                )
                call.respondText(MOCK_OPENAI_RESPONSE, ContentType.Application.Json)
            }
        }

        loadKoinModules(module {
            single<LlmProxyConfig> {
                LlmProxyConfig(
                    enabled = true,
                    providers = mapOf("anthropic" to LlmProxyProviderConfig(baseUrl = upstreamPath))
                )
            }
        })

        val agentName = "test-agent"
        val id: SessionIdentifier = client.authenticatedPost(LocalSessions.Session()) {
            setBody(sessionRequest {
                agentGraphRequest {
                    agent(PuppetDebugAgent.identifier) {
                        name = agentName
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                    isolateAllAgents()
                }
            })
        }.body()

        val session = localSessionManager.getSessions(id.namespace).first()
        val secret = session.agents[agentName].shouldNotBeNull().secret

        try {
            val key = "sk-ant-agent-key-${UUID.randomUUID()}"
            val response = client.post("/llm-proxy/$secret/anthropic/v1/messages") {
                contentType(ContentType.Application.Json)
                header("x-api-key", key)
                setBody("""{"model":"claude-sonnet-4-20250514","messages":[{"role":"user","content":"hi"}]}""")
            }

            response.shouldBeOK()
            capturedHeaders.await()["x-api-key"].shouldNotBeNull().shouldContainExactly(key)
        } finally {
            session.cancelAndJoinAgents()
        }
    }


    // TODO: prototype runtime will be changed soon so that it will only use the proxy, the interface for that is WIP
//    val openaiApiKey = System.getenv("CORAL_TEST_OPENAI_API_KEY")
//
//    test("e2eProxyWithRealOpenai").config(
//        enabled = openaiApiKey != null,
//        invocationTimeout = 1.minutes
//    ) {
//        loadKoinModules(module {
//            single<LlmProxyConfig> {
//                LlmProxyConfig(
//                    enabled = true,
//                    providers = mapOf("openai" to LlmProxyProviderConfig(apiKey = openaiApiKey!!))
//                )
//            }
//        })
//
//        multiAgentPayloadTest(
//            PrototypeModelProvider.OpenAI(
//                PrototypeString.Inline("proxy-managed"),
//                PrototypeString.Inline("gpt-4.1-nano"),
//                url = PrototypeApiUrl.Proxy
//            )
//        )
//    }
})
