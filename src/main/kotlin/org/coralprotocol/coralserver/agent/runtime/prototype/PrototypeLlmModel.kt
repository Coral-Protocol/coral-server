package org.coralprotocol.coralserver.agent.runtime.prototype

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable mirror of Koog's [LLMCapability]
 */
@Serializable
enum class PrototypeLlmCapability(val capability: LLMCapability) {
    @SerialName("speculation")
    SPECULATION(LLMCapability.Speculation),

    @SerialName("temperature")
    TEMPERATURE(LLMCapability.Temperature),

    @SerialName("tools")
    TOOLS(LLMCapability.Tools),

    @SerialName("tool_choice")
    TOOL_CHOICE(LLMCapability.ToolChoice),

    @SerialName("multiple_choices")
    MULTIPLE_CHOICES(LLMCapability.MultipleChoices),

    @SerialName("vision_image")
    VISION_IMAGE(LLMCapability.Vision.Image),

    @SerialName("vision_video")
    VISION_VIDEO(LLMCapability.Vision.Video),

    @SerialName("audio")
    AUDIO(LLMCapability.Audio),

    @SerialName("document")
    DOCUMENT(LLMCapability.Document),

    @SerialName("completion")
    COMPLETION(LLMCapability.Completion),

    @SerialName("prompt_caching")
    PROMPT_CACHING(LLMCapability.PromptCaching),

    @SerialName("thinking")
    THINKING(LLMCapability.Thinking),

    @SerialName("json_schema_basic")
    JSON_SCHEMA_BASIC(LLMCapability.Schema.JSON.Basic),

    @SerialName("json_schema_standard")
    JSON_SCHEMA_STANDARD(LLMCapability.Schema.JSON.Standard),

    @SerialName("openai_chat_completions")
    OPENAI_CHAT_COMPLETIONS(LLMCapability.OpenAIEndpoint.Completions),

    @SerialName("openai_responses")
    OPENAI_RESPONSES(LLMCapability.OpenAIEndpoint.Responses),
}

/**
 * A manually specified model, for models that Koog does not know about but that are served by an API compatible with a
 * [PrototypeClient], e.g., a self-hosted model behind an OpenAI compatible server.
 *
 * The model's ID is the proxied model name, and the model's provider is the provider of the [PrototypeClient].
 */
@Serializable
data class PrototypeLlmModel(
    val capabilities: List<PrototypeLlmCapability> = DEFAULT_CAPABILITIES,

    @SerialName("context_length")
    val contextLength: Long? = null,

    @SerialName("max_output_tokens")
    val maxOutputTokens: Long? = null
) {
    fun toLLModel(provider: LLMProvider, id: String) = LLModel(
        provider = provider,
        id = id,
        capabilities = capabilities.map { it.capability },
        contextLength = contextLength,
        maxOutputTokens = maxOutputTokens
    )

    companion object {
        /**
         * The minimum set of capabilities required by the prototype runtime with a chat completions API
         */
        val DEFAULT_CAPABILITIES = listOf(
            PrototypeLlmCapability.COMPLETION,
            PrototypeLlmCapability.TEMPERATURE,
            PrototypeLlmCapability.TOOLS,
            PrototypeLlmCapability.TOOL_CHOICE,
            PrototypeLlmCapability.OPENAI_CHAT_COMPLETIONS
        )
    }
}
