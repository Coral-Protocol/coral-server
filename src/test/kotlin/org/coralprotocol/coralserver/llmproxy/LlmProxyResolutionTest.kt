package org.coralprotocol.coralserver.llmproxy

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldMatchAll
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.graph.GraphAgentProxyRequest
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.agent.registry.ListAgentRegistrySource
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.config.LlmProxyProviderConfig
import org.coralprotocol.coralserver.routes.api.v1.LocalSessions
import org.coralprotocol.coralserver.session.LocalSessionManager
import org.coralprotocol.coralserver.utils.dsl.AgentLlmConfigBuilder
import org.coralprotocol.coralserver.utils.dsl.GraphAgentRequestBuilder
import org.coralprotocol.coralserver.utils.dsl.registryAgent
import org.coralprotocol.coralserver.utils.dsl.sessionRequest
import org.koin.core.component.inject

private sealed interface ExpectedResponse {
    data class Fails(val status: HttpStatusCode) : ExpectedResponse
    data class Succeeds(val proxyValidator: Map<String, LlmProxiedModel>.() -> Unit) : ExpectedResponse
}

class LlmProxyResolutionTest : CoralTest({
    val openAIAnyModel = LlmProxyProviderConfig(
        name = "Open AI, any model",
        format = LlmProviderFormat.OpenAI,
        apiKey = "dummy",
        baseUrl = "dummy",
        allowAnyModel = true
    )

    val anthropicAnyModel = LlmProxyProviderConfig(
        name = "Anthropic, any model",
        format = LlmProviderFormat.Anthropic,
        apiKey = "dummy",
        baseUrl = "dummy",
        allowAnyModel = true
    )

    fun openAIConfig(vararg models: String) = LlmProxyProviderConfig(
        name = "OpenAI, ${models.joinToString(", ")}",
        format = LlmProviderFormat.OpenAI,
        models = models.toSet(),
        apiKey = "dummy",
        baseUrl = "dummy"
    )

    fun anthropicConfig(vararg models: String) = LlmProxyProviderConfig(
        name = "Anthropic, ${models.joinToString(", ")}",
        format = LlmProviderFormat.Anthropic,
        models = models.toSet(),
        apiKey = "dummy",
        baseUrl = "dummy"
    )

    suspend fun modelResolutionTest(
        configs: List<LlmProxyProviderConfig>,
        expectedResponse: ExpectedResponse,
        graphAgentRequestBuilder: GraphAgentRequestBuilder.() -> Unit = {},
        agentConfigLlmBuilder: AgentLlmConfigBuilder.() -> Unit
    ) {
        val agentRegistry by inject<AgentRegistry>()
        val llmProxyService by inject<LlmProxyService>()
        val localSessionManager by inject<LocalSessionManager>()
        val client by inject<HttpClient>()

        val proxies = CompletableDeferred<Map<String, LlmProxiedModel>>()

        val agent = registryAgent("agent") {
            llm {
                agentConfigLlmBuilder()
            }
            runtime(FunctionRuntime { executionContext, _ ->
                proxies.complete(executionContext.graphAgent.proxies)
            })
        }

        agentRegistry.sources.clear()
        agentRegistry.sources.add(ListAgentRegistrySource("test", listOf(agent)))
        
        llmProxyService.providers.clear()
        llmProxyService.providers.addAll(configs)

        val response = client.authenticatedPost(LocalSessions.Session()) {
            setBody(
                sessionRequest {
                    agentGraphRequest {
                        agent(agent.identifier) {
                            graphAgentRequestBuilder()
                        }
                        isolateAllAgents()
                    }
                }
            )
        }

        localSessionManager.waitAllSessions()

        when (expectedResponse) {
            is ExpectedResponse.Fails -> {
                response.shouldHaveStatus(expectedResponse.status)
            }

            is ExpectedResponse.Succeeds -> {
                response.shouldHaveStatus(HttpStatusCode.OK)
                expectedResponse.proxyValidator(proxies.await())
            }
        }
    }

    test("openAINoConfigs") {
        modelResolutionTest(listOf(), ExpectedResponse.Fails(HttpStatusCode.InternalServerError)) {
            proxy("OpenAI", LlmProviderFormat.OpenAI)
        }
    }

    test("anthropicNoConfigs") {
        modelResolutionTest(listOf(), ExpectedResponse.Fails(HttpStatusCode.InternalServerError)) {
            proxy("Anthropic", LlmProviderFormat.Anthropic)
        }
    }

    test("openAIOnlyAnthropicConfig") {
        modelResolutionTest(listOf(anthropicAnyModel), ExpectedResponse.Fails(HttpStatusCode.InternalServerError)) {
            proxy("OpenAI", LlmProviderFormat.OpenAI)
        }
    }

    test("anthropicOnlyOpenAIConfig") {
        modelResolutionTest(listOf(openAIAnyModel), ExpectedResponse.Fails(HttpStatusCode.InternalServerError)) {
            proxy("Anthropic", LlmProviderFormat.Anthropic)
        }
    }

    test("openAIModelMismatch") {
        modelResolutionTest(
            listOf(openAIConfig("test model 123")),
            ExpectedResponse.Fails(HttpStatusCode.InternalServerError)
        ) {
            proxy("OpenAI", LlmProviderFormat.OpenAI, "test model")
        }
    }

    test("anthropicModelMismatch") {
        modelResolutionTest(
            listOf(anthropicConfig("test model 123")),
            ExpectedResponse.Fails(HttpStatusCode.InternalServerError)
        ) {
            proxy("Anthropic", LlmProviderFormat.Anthropic, "test model")
        }
    }

    test("openAIWrongProvider") {
        modelResolutionTest(
            listOf(anthropicConfig("test model")),
            ExpectedResponse.Fails(HttpStatusCode.InternalServerError)
        ) {
            proxy("OpenAI", LlmProviderFormat.OpenAI, "test model")
        }
    }

    test("anthropicWrongProvider") {
        modelResolutionTest(
            listOf(openAIConfig("test model")),
            ExpectedResponse.Fails(HttpStatusCode.InternalServerError)
        ) {
            proxy("Anthropic", LlmProviderFormat.Anthropic, "test model")
        }
    }

    test("testAnyModel") {
        val requestName = "MAIN"
        val modelName = "test model"

        for (config in listOf(openAIAnyModel, anthropicAnyModel)) {
            modelResolutionTest(listOf(config), ExpectedResponse.Succeeds {
                shouldMatchAll(requestName to {
                    it.providerConfig.shouldBeEqual(config)
                    it.modelName.shouldBeEqual(modelName)
                })
            }) {
                proxy(requestName, config.format, modelName)
            }
        }
    }

    test("testMultipleProxies") {
        val openAIRequest = "OPENAI"
        val anthropicRequest = "ANTHROPIC"
        val modelName = "test model"

        modelResolutionTest(listOf(openAIAnyModel, anthropicAnyModel), ExpectedResponse.Succeeds {
            shouldMatchAll(
                openAIRequest to {
                    it.providerConfig.shouldBeEqual(openAIAnyModel)
                    it.modelName.shouldBeEqual(modelName)
                },
                anthropicRequest to {
                    it.providerConfig.shouldBeEqual(anthropicAnyModel)
                    it.modelName.shouldBeEqual(modelName)
                })
        }) {
            proxy(openAIRequest, LlmProviderFormat.OpenAI, modelName)
            proxy(anthropicRequest, LlmProviderFormat.Anthropic, modelName)
        }
    }

    test("testPrioritizeSpecificModel") {
        val requestName = "MAIN"
        val modelName = "test model"
        val specificModelConfig = openAIConfig(modelName)

        modelResolutionTest(listOf(openAIAnyModel, specificModelConfig), ExpectedResponse.Succeeds {
            shouldMatchAll(requestName to {
                it.providerConfig.shouldBeEqual(specificModelConfig)
                it.modelName.shouldBeEqual(modelName)
            })
        }) {
            proxy(requestName, LlmProviderFormat.OpenAI, modelName)
        }
    }

    test("testAnyModelFallback") {
        val requestName = "MAIN"
        val modelName = "test model"
        val specificModelConfig = openAIConfig("another model")

        modelResolutionTest(listOf(openAIAnyModel, specificModelConfig), ExpectedResponse.Succeeds {
            shouldMatchAll(requestName to {
                it.providerConfig.shouldBeEqual(openAIAnyModel)
                it.modelName.shouldBeEqual(modelName)
            })
        }) {
            proxy(requestName, LlmProviderFormat.OpenAI, modelName)
        }
    }

    test("testResolutionOverride") {
        val requestName = "MAIN"
        val modelName = "test model"
        val overrideModelName = "override model"
        val specificModelConfig = openAIConfig(modelName)

        modelResolutionTest(listOf(openAIAnyModel, specificModelConfig), ExpectedResponse.Succeeds {
            shouldMatchAll(requestName to {
                it.providerConfig.shouldBeEqual(openAIAnyModel)
                it.modelName.shouldBeEqual(overrideModelName)
            })
        }, {
            proxyOverride(requestName, GraphAgentProxyRequest(openAIAnyModel.name, overrideModelName))
        }) {
            proxy(requestName, LlmProviderFormat.OpenAI, modelName)
        }
    }

    test("testOverrideBadFormat") {
        val requestName = "MAIN"
        val modelName = "test model"

        modelResolutionTest(
            listOf(openAIAnyModel, anthropicAnyModel),
            ExpectedResponse.Fails(HttpStatusCode.InternalServerError),
            {
                proxyOverride(requestName, GraphAgentProxyRequest(anthropicAnyModel.name, modelName))
            }) {
            proxy(requestName, LlmProviderFormat.OpenAI, modelName)
        }
    }

    test("testOverrideBadModel") {
        val requestName = "MAIN"
        val modelName = "test model"
        val specificModelConfig = openAIConfig("another model")

        modelResolutionTest(
            listOf(openAIAnyModel, specificModelConfig),
            ExpectedResponse.Fails(HttpStatusCode.InternalServerError),
            {
                proxyOverride(requestName, GraphAgentProxyRequest(specificModelConfig.name, modelName))
            }) {
            proxy(requestName, LlmProviderFormat.OpenAI, modelName)
        }
    }
})