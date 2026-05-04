package org.coralprotocol.coralserver.dsl

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import org.coralprotocol.coralserver.agent.registry.AgentLlmProxyRequest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeClient
import org.coralprotocol.coralserver.config.LlmProxyProviderConfig
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat

enum class CoralLlmProxyConfiguration(
    val envVar: String,
    val format: LlmProviderFormat,
    val prototypeClient: PrototypeClient
) {
    OPENAI("CORAL_OPENAI_API_KEY", LlmProviderFormat.OpenAI, PrototypeClient.OPEN_AI),
    ANTHROPIC("CORAL_ANTHROPIC_API_KEY", LlmProviderFormat.Anthropic, PrototypeClient.ANTHROPIC),
}

data class CoralLlmProxy(
    val providerConfig: LlmProxyProviderConfig,
    val proxyRequest: AgentLlmProxyRequest,
    val prototypeClient: PrototypeClient
) {
    companion object {
        fun buildFromConfig(config: CoralLlmProxyConfiguration): CoralLlmProxy? {
            return System.getenv(config.envVar)?.let {
                CoralLlmProxy(
                    providerConfig = LlmProxyProviderConfig(
                        name = config.envVar,
                        format = config.format,
                        allowAnyModel = true,
                        apiKey = it,
                        baseUrl = when (config) {
                            CoralLlmProxyConfiguration.OPENAI -> OpenAIClientSettings().baseUrl
                            CoralLlmProxyConfiguration.ANTHROPIC -> AnthropicClientSettings().baseUrl
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
