package org.coralprotocol.coralserver.llmproxy.strategies

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.coralprotocol.coralserver.llmproxy.LlmProviderAuthStyle
import org.coralprotocol.coralserver.llmproxy.LlmProviderStrategy
import org.coralprotocol.coralserver.llmproxy.LlmUsage
import org.coralprotocol.coralserver.llmproxy.StreamTokenParser
import org.coralprotocol.coralserver.logging.LoggingInterface

object AnthropicStrategy : LlmProviderStrategy {
    override fun prepareStreamingRequest(
        requestBody: String,
        json: Json,
        logger: LoggingInterface
    ): String = requestBody

    override fun extractBufferedTokens(responseBody: String, json: Json) =
        LlmUsage.extractLlmUsage(responseBody, json)

    override fun createStreamParser(json: Json): StreamTokenParser = AnthropicStreamParser(json)

    override val authStyle: LlmProviderAuthStyle
        get() = LlmProviderAuthStyle.Custom("x-api-key")

    override val defaultHeaders: Map<String, String> = mapOf("anthropic-version" to "2023-06-01")
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
                    val usage = obj["message"]?.jsonObject?.let { LlmUsage.extractLlmUsage(it, json) }
                    inputTokens = usage?.inputTokens ?: inputTokens
                }

                "message_delta" -> {
                    val usage = LlmUsage.extractLlmUsage(obj, json)
                    outputTokens = usage?.outputTokens ?: outputTokens
                }
            }
        } catch (_: SerializationException) {
            // ignored, not containing usage information is not an error
        }
    }
}