package org.coralprotocol.coralserver.llmproxy

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_LLM_PROXY
import org.koin.core.component.get
import org.koin.core.qualifier.named

class LlmProviderStrategyTest : CoralTest({

    val json = Json { ignoreUnknownKeys = true }

    test("extractsPromptTokensAndCompletionTokens") {
        val promptTokens = 100L
        val completionTokens = 25L
        val totalTokens = 125L

        val body =
            """{"usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens,"total_tokens":$totalTokens}}"""
        val usage = OpenAIStrategy.extractBufferedTokens(body, json).shouldNotBeNull()
        usage.inputTokens.shouldBe(promptTokens)
        usage.outputTokens.shouldBe(completionTokens)
    }

    test("returnsNullsForMissingOrMalformedInput") {
        OpenAIStrategy.extractBufferedTokens("""{"id":"test"}""", json).shouldBeNull()
        OpenAIStrategy.extractBufferedTokens("not json", json).shouldBeNull()
    }

    test("injectsStreamOptionsWhenAbsentPreservesWhenPresent") {
        val logger = get<Logger>(named(LOGGER_LLM_PROXY))

        val without = """{"model":"gpt-4","stream":true,"messages":[]}"""
        OpenAIStrategy.prepareStreamingRequest(without, json, logger).shouldContain("include_usage")

        val with = """{"model":"gpt-4","stream_options":{"include_usage":false}}"""
        OpenAIStrategy.prepareStreamingRequest(with, json, logger).shouldBe(with)
    }

    test("openaiStreamParserExtractsTokensFromFinalChunk") {
        val promptTokens = 10L
        val completionTokens = 2L

        val parser = OpenAIStrategy.createStreamParser(json)
        parser.processLine("""data: {"choices":[{"delta":{"content":"Hello"}}]}""")
        parser.processLine("""data: {"choices":[{"delta":{"content":" world"}}],"usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens}}""")
        parser.processLine("data: [DONE]")

        parser.inputTokens.shouldBe(promptTokens)
        parser.outputTokens.shouldBe(completionTokens)
        parser.chunkCount.shouldBe(2)
    }

    test("anthropicStreamParserExtractsTokensFromMessageStartAndDelta") {
        val parser = AnthropicStrategy.createStreamParser(json)

        val inputTokens = 42L
        val outputTokens = 2L

        parser.processLine("event: message_start")
        parser.processLine("""data: {"type":"message_start","message":{"usage":{"input_tokens":$inputTokens}}}""")

        parser.processLine("event: content_block_delta")
        parser.processLine("""data: {"type":"content_block_delta","delta":{"text":"Hello"}}""")

        parser.processLine("event: message_delta")
        parser.processLine("""data: {"type":"message_delta","usage":{"output_tokens":$outputTokens}}""")

        parser.inputTokens.shouldBe(inputTokens)
        parser.outputTokens.shouldBe(outputTokens)
        parser.chunkCount.shouldBe(3)
    }
})
