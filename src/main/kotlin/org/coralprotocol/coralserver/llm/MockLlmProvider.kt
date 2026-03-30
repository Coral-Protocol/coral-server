package org.coralprotocol.coralserver.llm

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

data class MockLlmResponse(
    val body: String,
    val contentType: ContentType,
    val inputTokens: Long,
    val outputTokens: Long
)

object MockLlmProvider {
    private const val MOCK_INPUT_TOKENS = 10L
    private const val MOCK_OUTPUT_TOKENS = 3L

    fun generate(model: String?, isStreaming: Boolean): MockLlmResponse {
        val resolvedModel = model ?: "mock-model"

        return if (isStreaming) {
            val body = buildString {
                appendLine("data: ${streamChunk(resolvedModel, "Hello", null)}")
                appendLine()
                appendLine("data: ${streamChunk(resolvedModel, " from", null)}")
                appendLine()
                appendLine("data: ${streamChunk(resolvedModel, " mock", "stop", MOCK_INPUT_TOKENS, MOCK_OUTPUT_TOKENS)}")
                appendLine()
                appendLine("data: [DONE]")
                appendLine()
            }
            MockLlmResponse(body, ContentType.Text.EventStream, MOCK_INPUT_TOKENS, MOCK_OUTPUT_TOKENS)
        } else {
            val body = bufferedResponse(resolvedModel, "Hello from mock", MOCK_INPUT_TOKENS, MOCK_OUTPUT_TOKENS)
            MockLlmResponse(body, ContentType.Application.Json, MOCK_INPUT_TOKENS, MOCK_OUTPUT_TOKENS)
        }
    }

    private fun bufferedResponse(model: String, content: String, promptTokens: Long, completionTokens: Long): String =
        """{"id":"mock-1","object":"chat.completion","model":"$model","choices":[{"index":0,"message":{"role":"assistant","content":"$content"},"finish_reason":"stop"}],"usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens,"total_tokens":${promptTokens + completionTokens}}}"""

    private fun streamChunk(model: String, content: String, finishReason: String?, promptTokens: Long? = null, completionTokens: Long? = null): String {
        val usagePart = if (promptTokens != null) ""","usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens,"total_tokens":${promptTokens + (completionTokens ?: 0)}}""" else ""
        val finishPart = if (finishReason != null) """"$finishReason"""" else "null"
        return """{"id":"mock-1","object":"chat.completion.chunk","model":"$model","choices":[{"index":0,"delta":{"content":"$content"},"finish_reason":$finishPart}]$usagePart}"""
    }
}
