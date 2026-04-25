package org.coralprotocol.coralserver.llmproxy

import org.coralprotocol.coralserver.llmproxy.strategies.AnthropicStrategy
import org.coralprotocol.coralserver.llmproxy.strategies.OpenAIStrategy

sealed interface LlmProviderFormat : LlmProviderStrategy {
    object OpenAI : LlmProviderFormat, LlmProviderStrategy by OpenAIStrategy {
        override fun toString(): String {
            return "OpenAI"
        }
    }

    object Anthropic : LlmProviderFormat, LlmProviderStrategy by AnthropicStrategy {
        override fun toString(): String {
            return "Anthropic"
        }
    }
}