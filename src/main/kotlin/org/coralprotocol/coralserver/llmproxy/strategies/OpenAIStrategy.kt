package org.coralprotocol.coralserver.llmproxy.strategies

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.llmproxy.LlmProviderAuthStyle
import org.coralprotocol.coralserver.llmproxy.LlmProviderStrategy
import org.coralprotocol.coralserver.llmproxy.LlmUsage
import org.coralprotocol.coralserver.llmproxy.StreamTokenParser
import org.coralprotocol.coralserver.logging.LoggingInterface

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

    override fun extractBufferedTokens(responseBody: String, json: Json) = LlmUsage.extractLlmUsage(responseBody, json)
    override fun createStreamParser(json: Json): StreamTokenParser = OpenAIStreamParser(json)

    override val authStyle: LlmProviderAuthStyle = LlmProviderAuthStyle.Bearer
    override val defaultHeaders: Map<String, String> = emptyMap()
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
            val usage = LlmUsage.extractLlmUsage(line.removePrefix("data: "), json)

            inputTokens = usage?.inputTokens ?: inputTokens
            outputTokens = usage?.outputTokens ?: outputTokens
        } catch (_: SerializationException) {
            // ignored, not containing usage information is not an error
        }
    }
}