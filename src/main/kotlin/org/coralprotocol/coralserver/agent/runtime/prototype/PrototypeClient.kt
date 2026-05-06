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
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.exceptions.PrototypeRuntimeException
import org.coralprotocol.coralserver.llmproxy.LlmProxiedModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

@Serializable
enum class PrototypeClient(val models: Any) : KoinComponent {
    @SerialName("openai")
    OPEN_AI(OpenAIModels.Chat),

    @SerialName("openrouter")
    OPEN_ROUTER(OpenRouterModels),

    @SerialName("anthropic")
    ANTHROPIC(AnthropicModels);

    fun getPromptExecutor(baseUrl: String, apiKey: String) =
        when (this) {
            OPEN_AI -> MultiLLMPromptExecutor(
                OpenAILLMClient(
                    apiKey = apiKey,
                    baseClient = get(),
                    settings = OpenAIClientSettings(baseUrl = "$baseUrl/")
                )
            )

            OPEN_ROUTER -> MultiLLMPromptExecutor(
                OpenRouterLLMClient(
                    apiKey = apiKey,
                    baseClient = get(),
                    settings = OpenRouterClientSettings(baseUrl = baseUrl)
                )
            )

            ANTHROPIC -> MultiLLMPromptExecutor(
                AnthropicLLMClient(
                    apiKey = apiKey,
                    baseClient = get(),
                    settings = AnthropicClientSettings(baseUrl = baseUrl)
                )
            )
        }

    fun getLlmModel(model: LlmProxiedModel): LLModel {
        val models = models::class.members
            .filter { member -> member.returnType.classifier == LLModel::class }
            .mapNotNull { member -> member.call() as? LLModel }

        val name = serializer().descriptor.getElementName(ordinal)
        return models.firstOrNull { it.id == model.modelName }
            ?: throw PrototypeRuntimeException.BadModel(
                "model \"${model.modelName}\" is not provided prototype runtime client \"$name\".  Available models: ${
                    models.joinToString(
                        ", "
                    ) { it.id }
                }"
            )
    }
}