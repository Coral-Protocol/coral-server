@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.llmproxy

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.logging.LoggingInterface

sealed class LlmProviderAuthStyle {
    data object Bearer : LlmProviderAuthStyle()
    data class Custom(val headerName: String) : LlmProviderAuthStyle()
}

interface LlmProviderStrategy {
    fun prepareStreamingRequest(requestBody: String, json: Json, logger: LoggingInterface): String
    fun extractBufferedTokens(responseBody: String, json: Json): LlmUsage?
    fun createStreamParser(json: Json): StreamTokenParser

    val authStyle: LlmProviderAuthStyle
    val defaultHeaders: Map<String, String>
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

@Serializable
@JsonIgnoreUnknownKeys
data class LlmUsage(
    @JsonNames("prompt_tokens", "input_tokens")
    val inputTokens: Long? = null,

    @JsonNames("completion_tokens", "output_tokens")
    val outputTokens: Long? = null,
) {
    @Serializable
    @JsonIgnoreUnknownKeys
    private data class LlmUsageWrapper(val usage: LlmUsage? = null)

    companion object {
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
    }
}