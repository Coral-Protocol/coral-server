package org.coralprotocol.coralserver.utils

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.AgentLlmProxyRequest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeClient
import org.coralprotocol.coralserver.config.LlmProxyProviderConfig
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat

enum class TestProxyConfiguration(
    val envVar: String,
    val format: LlmProviderFormat,
    val prototypeClient: PrototypeClient
) {
    OPENAI("CORAL_TEST_OPENAI_API_KEY", LlmProviderFormat.OpenAI, PrototypeClient.OPEN_AI),
    ANTHROPIC("CORAL_TEST_ANTHROPIC_API_KEY", LlmProviderFormat.Anthropic, PrototypeClient.ANTHROPIC),
}

/**
 * The purpose of this class is to tie the definition of a [LlmProxyProviderConfig] with a [AgentLlmProxyRequest].
 * [CoralTest] can register a [TestProxy] for each of the [TestProxyConfiguration]s. Any non-null [TestProxy] can be
 * used by tests, where it is guaranteed that [AgentLlmProxyRequest] will resolve to the paired [LlmProxyProviderConfig].
 */
data class TestProxy(
    val providerConfig: LlmProxyProviderConfig,
    val proxyRequest: AgentLlmProxyRequest,
    val prototypeClient: PrototypeClient
) {
    companion object {
        fun buildFromConfig(config: TestProxyConfiguration): TestProxy? {
            return System.getenv(config.envVar)?.let {
                TestProxy(
                    providerConfig = LlmProxyProviderConfig(
                        name = config.envVar,
                        format = config.format,
                        allowAnyModel = true,
                        apiKey = it,
                        baseUrl = when (config) {
                            TestProxyConfiguration.OPENAI -> OpenAIClientSettings().baseUrl
                            TestProxyConfiguration.ANTHROPIC -> AnthropicClientSettings().baseUrl
                        }
                    ),
                    proxyRequest = AgentLlmProxyRequest(
                        name = config.envVar,
                        format = config.format
                    ),
                    prototypeClient = config.prototypeClient
                )
            }
        }
    }
}