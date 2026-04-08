package org.coralprotocol.coralserver.llmproxy

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json

class LlmProviderStrategyTest : FunSpec({

    val json = Json { ignoreUnknownKeys = true }

    test("extractsPromptTokensAndCompletionTokens") {
        val body = """{"usage":{"prompt_tokens":100,"completion_tokens":25,"total_tokens":125}}"""
        val (input, output) = OpenAIStrategy.extractBufferedTokens(body, json)
        input.shouldBe(100)
        output.shouldBe(25)
    }

    test("returnsNullsForMissingOrMalformedInput") {
        OpenAIStrategy.extractBufferedTokens("""{"id":"test"}""", json).let { (i, o) ->
            i.shouldBeNull(); o.shouldBeNull()
        }
        OpenAIStrategy.extractBufferedTokens("not json", json).let { (i, o) ->
            i.shouldBeNull(); o.shouldBeNull()
        }
    }

    test("injectsStreamOptionsWhenAbsentPreservesWhenPresent") {
        val without = """{"model":"gpt-4","stream":true,"messages":[]}"""
        OpenAIStrategy.prepareStreamingRequest(without, json).shouldContain("include_usage")

        val with = """{"model":"gpt-4","stream_options":{"include_usage":false}}"""
        OpenAIStrategy.prepareStreamingRequest(with, json).shouldBe(with)
    }

    test("openaiStreamParserExtractsTokensFromFinalChunk") {
        val parser = OpenAIStrategy.createStreamParser(json)
        parser.processLine("""data: {"choices":[{"delta":{"content":"Hello"}}]}""")
        parser.processLine("""data: {"choices":[{"delta":{"content":" world"}}],"usage":{"prompt_tokens":10,"completion_tokens":2}}""")
        parser.processLine("data: [DONE]")

        parser.inputTokens.shouldBe(10)
        parser.outputTokens.shouldBe(2)
        parser.chunkCount.shouldBe(2)
    }

    test("anthropicStreamParserExtractsTokensFromMessageStartAndDelta") {
        val parser = AnthropicStrategy.createStreamParser(json)

        parser.processLine("event: message_start")
        parser.processLine("""data: {"type":"message_start","message":{"usage":{"input_tokens":42}}}""")

        parser.processLine("event: content_block_delta")
        parser.processLine("""data: {"type":"content_block_delta","delta":{"text":"Hello"}}""")

        parser.processLine("event: message_delta")
        parser.processLine("""data: {"type":"message_delta","usage":{"output_tokens":17}}""")

        parser.inputTokens.shouldBe(42)
        parser.outputTokens.shouldBe(17)
        parser.chunkCount.shouldBe(3)
    }
})
