@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.llmproxy

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.logging.LoggingInterface

@Serializable
@JsonIgnoreUnknownKeys
data class LlmUsage(
    @JsonNames("prompt_tokens", "input_tokens")
    val inputTokens: Long? = null,

    @JsonNames("completion_tokens", "output_tokens")
    val outputTokens: Long? = null,
)

@Serializable
@JsonIgnoreUnknownKeys
private data class LlmUsageWrapper(val usage: LlmUsage? = null)

interface LlmProviderStrategy {
    fun prepareStreamingRequest(requestBody: String, json: Json, logger: LoggingInterface): String = requestBody
    fun extractBufferedTokens(responseBody: String, json: Json): LlmUsage?
    fun createStreamParser(json: Json): StreamTokenParser
}

/**
 * Stateful parser for a single SSE stream. Processes raw SSE lines and extracts token usage.
 * Each streaming request should create a fresh instance via [LlmProviderStrategy.createStreamParser].
 */
interface StreamTokenParser {
    fun processLine(line: String)
    val inputTokens: Long?
    val outputTokens: Long?
    val chunkCount: Int
}

object OpenAIStrategy : LlmProviderStrategy {
    override fun prepareStreamingRequest(requestBody: String, json: Json, logger: LoggingInterface): String {
        return try {
            val obj = json.decodeFromString<JsonObject>(requestBody)
            if (obj.containsKey("stream_options")) return requestBody
            val modified = buildJsonObject {
                obj.forEach { (key, value) -> put(key, value) }
                putJsonObject("stream_options") { put("include_usage", true) }
            }
            json.encodeToString(JsonObject.serializer(), modified)
        } catch (e: Exception) {
            logger.error(e) { "Failed to inject stream_options into request body" }
            requestBody
        }
    }

    override fun extractBufferedTokens(responseBody: String, json: Json) = extractLlmUsage(responseBody, json)
    override fun createStreamParser(json: Json): StreamTokenParser = OpenAIStreamParser(json)
}

object AnthropicStrategy : LlmProviderStrategy {
    override fun extractBufferedTokens(responseBody: String, json: Json) = extractLlmUsage(responseBody, json)
    override fun createStreamParser(json: Json): StreamTokenParser = AnthropicStreamParser(json)
}

/**
 * OpenAI SSE format: `data: {json}` lines, `data: [DONE]` terminator.
 * Usage appears in the final chunk when `stream_options.include_usage=true`.
 */
private class OpenAIStreamParser(private val json: Json) : StreamTokenParser {
    override var inputTokens: Long? = null; private set
    override var outputTokens: Long? = null; private set
    override var chunkCount: Int = 0; private set

    override fun processLine(line: String) {
        if (!line.startsWith("data: ") || line.startsWith("data: [DONE]")) return
        chunkCount++
        try {
            val usageWrapper = json.decodeFromString<LlmUsageWrapper>(line.removePrefix("data: "))

            inputTokens = usageWrapper.usage?.inputTokens ?: inputTokens
            outputTokens = usageWrapper.usage?.outputTokens ?: outputTokens
        } catch (_: SerializationException) {
            // ignored, not containing usage information is not an error
        }
    }
}

/**
 * Anthropic SSE format: `event: {type}` + `data: {json}` pairs.
 * Input tokens in `message_start` event, output tokens in `message_delta` event.
 */
private class AnthropicStreamParser(private val json: Json) : StreamTokenParser {
    override var inputTokens: Long? = null; private set
    override var outputTokens: Long? = null; private set
    override var chunkCount: Int = 0; private set
    private var lastEventType: String? = null

    override fun processLine(line: String) {
        if (line.startsWith("event: ")) {
            lastEventType = line.removePrefix("event: ").trim()
            return
        }

        if (!line.startsWith("data: ")) return
        chunkCount++

        try {
            val obj = json.decodeFromString<JsonObject>(line.removePrefix("data: "))
            when (lastEventType) {
                "message_start" -> {
                    val usage = obj["message"]?.jsonObject?.let { extractLlmUsage(it, json) }
                    inputTokens = usage?.inputTokens ?: inputTokens
                }

                "message_delta" -> {
                    val usage = extractLlmUsage(obj, json)
                    outputTokens = usage?.outputTokens ?: outputTokens
                }
            }
        } catch (_: SerializationException) {
            // ignored, not containing usage information is not an error
        }
    }
}

fun extractLlmUsage(body: String, json: Json) =
    try {
        json.decodeFromString<LlmUsageWrapper>(body).usage
    } catch (_: SerializationException) {
        null
    }

fun extractLlmUsage(body: JsonObject, json: Json) =
    try {
        json.decodeFromJsonElement<LlmUsageWrapper>(body).usage
    } catch (_: SerializationException) {
        null
    }