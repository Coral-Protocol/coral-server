package org.coralprotocol.coralserver.llm

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.session.SessionAgent

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
                appendLine("data: ${streamChunk(resolvedModel, "Hello")}")
                appendLine()
                appendLine("data: ${streamChunk(resolvedModel, " from")}")
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

    private fun bufferedResponse(model: String, content: String, promptTokens: Long, completionTokens: Long): String {
        return buildJsonObject {
            put("id", "mock-1")
            put("object", "chat.completion")
            put("model", model)
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("message") {
                        put("role", "assistant")
                        put("content", content)
                    }
                    put("finish_reason", "stop")
                }
            }
            putJsonObject("usage") {
                put("prompt_tokens", promptTokens)
                put("completion_tokens", completionTokens)
                put("total_tokens", promptTokens + completionTokens)
            }
        }.toString()
    }

    private fun streamChunk(
        model: String,
        content: String,
        finishReason: String? = null,
        promptTokens: Long? = null,
        completionTokens: Long? = null
    ): String {
        return buildJsonObject {
            put("id", "mock-1")
            put("object", "chat.completion.chunk")
            put("model", model)
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") {
                        put("content", content)
                    }
                    put("finish_reason", finishReason)
                }
            }
            if (promptTokens != null) {
                putJsonObject("usage") {
                    put("prompt_tokens", promptTokens)
                    put("completion_tokens", completionTokens ?: 0)
                    put("total_tokens", promptTokens + (completionTokens ?: 0))
                }
            }
        }.toString()
    }
}

suspend fun mockCheck(
    profile: LlmProviderProfile,
    agent: SessionAgent,
    model: String?,
    isStreaming: Boolean,
    call: ApplicationCall
): Boolean {
    if (profile == LlmProviderProfile.MOCK) {
        val startTime = System.currentTimeMillis()
        val mock = MockLlmProvider.generate(model, isStreaming)
        call.response.header("x-coral-mock", "true")
        call.respondText(mock.body, mock.contentType, HttpStatusCode.OK)
        val durationMs = System.currentTimeMillis() - startTime
        emitTelemetry(
            agent,
            LlmCallResult(
                "mock",
                model ?: "mock-model",
                mock.inputTokens,
                mock.outputTokens,
                durationMs,
                isStreaming,
                success = true
            )
        )
        return true
    }
    return false
}