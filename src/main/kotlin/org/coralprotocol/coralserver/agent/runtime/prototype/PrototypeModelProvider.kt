@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime.prototype

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.exceptions.PrototypeRuntimeException
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext


@Serializable
@JsonClassDiscriminator("provider")
sealed class PrototypeModelProvider {
    abstract val key: PrototypeString
    abstract val name: PrototypeString
    abstract val url: PrototypeApiUrl?
    abstract val modelClass: Any

    abstract fun getExecutor(executionContext: SessionAgentExecutionContext): PromptExecutor

    fun getModel(executionContext: SessionAgentExecutionContext): LLModel {
        val models = modelClass::class.members
            .filter { member -> member.returnType.classifier == LLModel::class }
            .mapNotNull { member -> member.call(modelClass) as? LLModel }

        val modelName = name.resolve(executionContext)
        return models.firstOrNull { it.id == modelName }
            ?: throw PrototypeRuntimeException.BadModel(
                "model \"$modelName\" is not provided by \"${serializer().descriptor.serialName}\".  Available models: ${
                    models.joinToString(
                        ", "
                    ) { it.id }
                }"
            )
    }

    fun getModelIdentifier(executionContext: SessionAgentExecutionContext): String =
        "${serializer().descriptor.serialName}/${name.resolve(executionContext)}"

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override val key: PrototypeString,
        override val name: PrototypeString,
        override val url: PrototypeApiUrl? = null,
    ) : PrototypeModelProvider() {
        override fun getExecutor(executionContext: SessionAgentExecutionContext): PromptExecutor =
            SingleLLMPromptExecutor(
                OpenAILLMClient(
                    apiKey = key.resolve(executionContext),
                    settings = if (url == null) OpenAIClientSettings() else OpenAIClientSettings(
                        baseUrl = url.resolve(executionContext)
                    )
                )
            )

        override val modelClass: Any
            get() = OpenAIModels.Chat
    }

    @Serializable
    @SerialName("anthropic")
    data class Anthropic(
        override val key: PrototypeString,
        override val name: PrototypeString,
        override val url: PrototypeApiUrl? = null,
    ) : PrototypeModelProvider() {
        override fun getExecutor(executionContext: SessionAgentExecutionContext): PromptExecutor =
            SingleLLMPromptExecutor(
                AnthropicLLMClient(
                    apiKey = key.resolve(executionContext),
                    settings = if (url == null) AnthropicClientSettings() else AnthropicClientSettings(
                        baseUrl = url.resolve(executionContext)
                    )
                )
            )

        override val modelClass: Any
            get() = AnthropicModels
    }

    @Serializable
    @SerialName("openrouter")
    data class OpenRouter(
        override val key: PrototypeString,
        override val name: PrototypeString,
        override val url: PrototypeApiUrl? = null,
    ) : PrototypeModelProvider() {
        override fun getExecutor(executionContext: SessionAgentExecutionContext): PromptExecutor =
            SingleLLMPromptExecutor(
                OpenRouterLLMClient(
                    apiKey = key.resolve(executionContext),
                    settings = if (url == null) OpenRouterClientSettings() else OpenRouterClientSettings(
                        baseUrl = url.resolve(executionContext)
                    )
                )
            )

        override val modelClass: Any
            get() = OpenRouterModels
    }
}
