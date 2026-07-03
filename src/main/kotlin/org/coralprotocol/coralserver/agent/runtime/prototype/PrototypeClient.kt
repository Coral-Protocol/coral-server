@file:Suppress("UnstableApiUsage") @file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime.prototype

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.agent.exceptions.PrototypeRuntimeException
import org.coralprotocol.coralserver.llmproxy.LlmProxiedModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class OpenAICompatibleStrictToolDescriptorGenerator : OpenAICompatibleToolDescriptorSchemaGenerator() {
    override fun generate(toolDescriptor: ToolDescriptor): JsonObject {
        val tool = super.generate(toolDescriptor)
        return JsonObject(tool.toMap() + mapOf("strict" to JsonPrimitive(true)))
    }
}

@Serializable
@JsonClassDiscriminator("type")
@Suppress("unused")
sealed class PrototypeClient(@Transient val models: Any = Unit) {

    abstract fun getPromptExecutor(baseUrl: String, apiKey: String): PromptExecutor

    open suspend fun startIteration(context: AIAgentFunctionalContext, iteration: Int, iterationCount: Int) = Unit
    open suspend fun endIteration(context: AIAgentFunctionalContext, iteration: Int, iterationCount: Int) = Unit

    fun getLlmModel(model: LlmProxiedModel): LLModel {
        val models = models::class.members.filter { member -> member.returnType.classifier == LLModel::class }
            .mapNotNull { member -> member.call() as? LLModel }

        val name = serializer().descriptor.serialName
        return models.firstOrNull { it.id == model.modelName }
            ?: throw PrototypeRuntimeException.BadModel(
                "model \"${model.modelName}\" is not provided prototype runtime client \"$name\".  Available models: ${
                    models.joinToString(
                        ", "
                    ) { it.id }
                }")
    }


    @Serializable
    @SerialName("openai")
    data class OpenAI(val strict: Boolean = true) : PrototypeClient(OpenAIModels.Chat), KoinComponent {
        override fun getPromptExecutor(
            baseUrl: String, apiKey: String
        ): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
            OpenAILLMClient(
                httpClientFactory = KtorKoogHttpClient.Factory(baseClient = get()),
                apiKey = apiKey,
                settings = OpenAIClientSettings(baseUrl = "$baseUrl/"),
                toolsConverter = if (strict) OpenAICompatibleStrictToolDescriptorGenerator() else OpenAICompatibleToolDescriptorSchemaGenerator()
            )
        )
    }

    @Serializable
    @SerialName("openrouter")
    data class OpenRouter(val strict: Boolean = true) : PrototypeClient(OpenRouterModels), KoinComponent {
        override fun getPromptExecutor(
            baseUrl: String, apiKey: String
        ): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
            OpenRouterLLMClient(
                httpClientFactory = KtorKoogHttpClient.Factory(baseClient = get()),
                apiKey = apiKey,
                settings = OpenRouterClientSettings(baseUrl = baseUrl),
                toolsConverter = if (strict) OpenAICompatibleStrictToolDescriptorGenerator() else OpenAICompatibleToolDescriptorSchemaGenerator()
            )
        )
    }

    @Serializable
    @SerialName("anthropic")
    object Anthropic : PrototypeClient(AnthropicModels), KoinComponent {
        override fun getPromptExecutor(
            baseUrl: String, apiKey: String
        ): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
            AnthropicLLMClient(
                httpClientFactory = KtorKoogHttpClient.Factory(baseClient = get()),
                apiKey = apiKey,
                settings = AnthropicClientSettings(baseUrl = baseUrl)
            )
        )
    }

    @Serializable
    @SerialName("deepseek")
    data class DeepSeek(val strict: Boolean = true) : PrototypeClient(DeepSeekModels), KoinComponent {
        override fun getPromptExecutor(
            baseUrl: String, apiKey: String
        ): MultiLLMPromptExecutor = MultiLLMPromptExecutor(
            DeepSeekLLMClient(
                httpClientFactory = KtorKoogHttpClient.Factory(baseClient = get()),
                apiKey = apiKey,
                settings = DeepSeekClientSettings(baseUrl = baseUrl),
                toolsConverter = if (strict) OpenAICompatibleStrictToolDescriptorGenerator() else OpenAICompatibleToolDescriptorSchemaGenerator()
            )
        )

        override suspend fun startIteration(
            context: AIAgentFunctionalContext,
            iteration: Int,
            iterationCount: Int
        ) {
            // with deepseek, tool_choice: required and thinking cannot be used together
            context.llm.writeSession {
                changeLLMParams(
                    LLMParams(
                        additionalProperties = mapOf(
                            "thinking" to buildJsonObject {
                                put("type", "disabled")
                            }
                        )))
            }
        }
    }
}