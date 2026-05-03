package org.coralprotocol.coralserver.llmproxy

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.AddressConsumer
import org.coralprotocol.coralserver.config.LlmProxyProviderConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.modules.LLM_PROXY_HTTP_CLIENT
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.coralprotocol.coralserver.utils.shouldPostEvent
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.get
import org.koin.test.inject
import java.util.*
import kotlin.time.Duration.Companion.seconds

private const val totalTokens = 20L
private const val outputTokens = 5L
private const val inputTokens = 15L;

private val MOCK_OPENAI_RESPONSE = buildJsonObject {
    put("id", "chatcmpl-test")
    put("object", "chat.completion")
    put("model", "gpt-test")
    putJsonArray("choices") {
        addJsonObject {
            put("index", 0)
            putJsonObject("message") {
                put("role", "assistant")
                put("content", "Hello from upstream")
            }
            put("finish_reason", "stop")
        }
    }
    putJsonObject("usage") {
        put("prompt_tokens", inputTokens)
        put("completion_tokens", outputTokens)
        put("total_tokens", totalTokens)
    }
}

private val MOCK_OPENAI_REQUEST = buildJsonObject {
    put("model", "gpt-test")
    put("messages", JsonArray(listOf(buildJsonObject {
        put("role", "user")
        put("content", "test")
    })))
}

class LlmProxyTest : CoralTest({
    val modelName = "mock-model"
    val proxyName = "mock-proxy"

    suspend fun mockLlmProviderProxy(
        routeBlock: Routing.(baseUrl: String) -> Unit,
        block: suspend (
            baseUrl: String,
            apiKey: String,
            executionContext: SessionAgentExecutionContext,
            applicationRuntimeContext: ApplicationRuntimeContext
        ) -> Unit
    ) {
        val application = get<Application>()
        val client = get<HttpClient>()
        val localSessionManager by inject<LocalSessionManager>()

        val proxyApiKey = UUID.randomUUID().toString() // dummy
        val upstreamUrl = UUID.randomUUID().toString()
        application.routing {
            routeBlock(upstreamUrl)
        }

        // Important! The HTTP client used by the LLM proxy must be replaced to use the unnamed default client (ktor
        // test client), otherwise the above route will not be accessible to the LlmProxyService
        loadKoinModules(module {
            single(named(LLM_PROXY_HTTP_CLIENT)) {
                client
            }
        })

        val (session1, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair("agent") {
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                        registryAgent {
                            runtime(FunctionRuntime { executionContext, applicationRuntimeContext ->
                                val path = applicationRuntimeContext.getLlmProxyUrl(
                                    executionContext,
                                    AddressConsumer.LOCAL,
                                    proxyName
                                ).toString()

                                block(path, proxyApiKey, executionContext, applicationRuntimeContext)
                            })
                        }
                        proxy(
                            proxyName, LlmProxiedModel(
                                providerConfig = LlmProxyProviderConfig(
                                    name = proxyName,
                                    format = LlmProviderFormat.OpenAI,
                                    apiKey = proxyApiKey,
                                    baseUrl = upstreamUrl
                                ),
                                modelName = modelName
                            )
                        )
                    }
                ),
            )
        )

        session1.fullLifeCycle()
    }

    test("proxyForwardsBufferedRequestToUpstreamAndExtractsTokens").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()

        val capturedHeaders = CompletableDeferred<Headers>()

        mockLlmProviderProxy({ path ->
            post(path) {
                capturedHeaders.complete(call.request.headers)
                call.respond(MOCK_OPENAI_RESPONSE)
            }
        }) { path, apiKey, executionContext, _ ->
            val proxyCall = executionContext.session.shouldPostEvent<SessionEvent.LlmProxyCall>(3.seconds) {
                val response = client.post(path) {
                    contentType(ContentType.Application.Json)
                    bearerAuth("should-be-stripped")
                    setBody(MOCK_OPENAI_REQUEST)
                }

                response.shouldBeOK().body<JsonObject>().shouldBeEqual(MOCK_OPENAI_RESPONSE)

                val headers = capturedHeaders.await()

                // llm provider should NOT get the API key used by the agent, if any
                // only that which was specified in the config
                headers[HttpHeaders.Authorization].shouldNotBeNull().shouldBeEqual("Bearer $apiKey")
            }

            proxyCall.model.shouldBeEqual(modelName)
            proxyCall.provider.shouldBeEqual(proxyName)
            proxyCall.success.shouldBeTrue()
            proxyCall.streaming.shouldBeFalse()
            proxyCall.inputTokens.shouldNotBeNull().shouldBeEqual(inputTokens)
            proxyCall.outputTokens.shouldNotBeNull().shouldBeEqual(outputTokens)
        }
    }

    test("proxyForwardsQueryParametersToUpstream").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()
        val capturedQueryParameters = CompletableDeferred<Parameters>()

        mockLlmProviderProxy({ path ->
            post(path) {
                capturedQueryParameters.complete(call.request.queryParameters)
                call.respond(MOCK_OPENAI_RESPONSE)
            }
        }) { path, _, _, _ ->
            val url = buildUrl {
                takeFrom(path)
                parameters.append("limit", "20")
                parameters.append("after", "abc")
                parameters.append("tag", "x")
                parameters.append("tag", "y")
            }.toString()

            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                bearerAuth("should-be-stripped")
                setBody(MOCK_OPENAI_REQUEST)
            }

            response.shouldBeOK().body<JsonObject>().shouldBeEqual(MOCK_OPENAI_RESPONSE)


            val queryParameters = capturedQueryParameters.await()
            queryParameters["limit"].shouldNotBeNull().shouldBeEqual("20")
            queryParameters["after"].shouldNotBeNull().shouldBeEqual("abc")
            queryParameters.getAll("tag").shouldNotBeNull().shouldContainExactly(listOf("x", "y"))
        }
    }


    test("proxyStripsBadHeadersAndPreservesIncomingJsonContentType").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()
        val capturedHeaders = CompletableDeferred<Headers>()

        mockLlmProviderProxy({ path ->
            post(path) {
                capturedHeaders.complete(call.request.headers)
                call.respond(MOCK_OPENAI_RESPONSE)
            }
        }) { path, _, _, _ ->
            val response = client.post(path) {
                contentType(ContentType.parse("application/json; charset=utf-8"))
                header(HttpHeaders.Cookie, "session=abc")
                header("x-api-key", "should-be-stripped")
                setBody(MOCK_OPENAI_REQUEST)
            }

            response.shouldBeOK().body<JsonObject>().shouldBeEqual(MOCK_OPENAI_RESPONSE)

            val headers = capturedHeaders.await()
            headers[HttpHeaders.Cookie].shouldBeNull() // cookie should be stripped
            headers[HttpHeaders.ContentType].shouldNotBeNull().shouldBeEqual("application/json; charset=utf-8")
            headers["x-api-key"].shouldBeNull()
        }
    }


    test("proxyRejectsNonJsonBodyRequests").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()

        mockLlmProviderProxy({ path ->
            post(path) {
                call.respond(MOCK_OPENAI_RESPONSE)
            }
        }) { path, _, _, _ ->
            client.post(path) {
                contentType(ContentType.Text.Plain)
                setBody("not-json")
            }.shouldHaveStatus(HttpStatusCode.UnsupportedMediaType)
        }
    }

    test("proxyAllowsGetRequestsAndRejectsUnsupportedMethods").config(invocationTimeout = 15.seconds) {
        val client by inject<HttpClient>()

        mockLlmProviderProxy({ path ->
            get(path) {
                call.respond(HttpStatusCode.OK)
            }

            put(path) {
                call.respond(HttpStatusCode.OK)
            }
        }) { path, _, _, _ ->
            client.get(path).shouldBeOK()
            client.put(path).shouldHaveStatus(HttpStatusCode.MethodNotAllowed)
        }
    }
})
