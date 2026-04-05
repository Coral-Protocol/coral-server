package org.coralprotocol.coralserver.llmproxy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

interface LlmProviderStrategy {
    fun prepareStreamingRequest(requestBody: String, json: Json): String = requestBody
    fun extractBufferedTokens(responseBody: String, json: Json): Pair<Long?, Long?>
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
    override fun prepareStreamingRequest(requestBody: String, json: Json): String {
        return try {
            val obj = json.decodeFromString<JsonObject>(requestBody)
            if (obj.containsKey("stream_options")) return requestBody
            val modified = buildJsonObject {
                obj.forEach { (key, value) -> put(key, value) }
                putJsonObject("stream_options") { put("include_usage", true) }
            }
            json.encodeToString(JsonObject.serializer(), modified)
        } catch (_: Exception) {
            requestBody
        }
    }

    override fun extractBufferedTokens(responseBody: String, json: Json): Pair<Long?, Long?> {
        return extractUsageField(responseBody, json)
    }

    override fun createStreamParser(json: Json): StreamTokenParser = OpenAIStreamParser(json)
}

object AnthropicStrategy : LlmProviderStrategy {
    override fun extractBufferedTokens(responseBody: String, json: Json): Pair<Long?, Long?> {
        return extractUsageField(responseBody, json)
    }

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
            val (inp, out) = extractUsageField(line.removePrefix("data: "), json)
            if (inp != null) inputTokens = inp
            if (out != null) outputTokens = out
        } catch (_: Exception) { }
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
                    val usage = (obj["message"] as? JsonObject)?.get("usage") as? JsonObject
                    val inp = usage?.get("input_tokens")?.jsonPrimitive?.longOrNull
                    if (inp != null) inputTokens = inp
                }
                "message_delta" -> {
                    val usage = obj["usage"] as? JsonObject
                    val out = usage?.get("output_tokens")?.jsonPrimitive?.longOrNull
                    if (out != null) outputTokens = out
                }
            }
        } catch (_: Exception) { }
    }
}

private fun extractUsageField(body: String, json: Json): Pair<Long?, Long?> {
    return try {
        val obj = json.decodeFromString<JsonObject>(body)
        val usage = obj["usage"] as? JsonObject ?: return null to null
        val input = usage["prompt_tokens"]?.jsonPrimitive?.longOrNull
            ?: usage["input_tokens"]?.jsonPrimitive?.longOrNull
        val output = usage["completion_tokens"]?.jsonPrimitive?.longOrNull
            ?: usage["output_tokens"]?.jsonPrimitive?.longOrNull
        input to output
    } catch (_: Exception) {
        null to null
    }
}
