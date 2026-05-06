package org.coralprotocol.coralserver.llm

import io.kotest.core.test.config.DefaultTestConfig
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest

/**
 * This test should be run sparingly!
 */
class AnthropicTest : CoralTest({
    defaultTestConfig = DefaultTestConfig(
        enabledIf = ::hasAnthropicProxy
    )

    suspend fun anthropicPayloadTest(modelName: String) {
        multiAgentPayloadTest(anthropicProxy!!, modelName)
    }

    test("testClaude3Haiku") { anthropicPayloadTest("claude-3-haiku") }
    test("testClaudeHaiku45") { anthropicPayloadTest("claude-haiku-4-5") }
    test("testClaudeOpus40") { anthropicPayloadTest("claude-opus-4-0") }
    test("testClaudeOpus41") { anthropicPayloadTest("claude-opus-4-1") }
    test("testClaudeOpus45") { anthropicPayloadTest("claude-opus-4-5") }
    test("testClaudeOpus46") { anthropicPayloadTest("claude-opus-4-6") }
    test("testClaudeSonnet40") { anthropicPayloadTest("claude-sonnet-4-0") }
    test("testClaudeSonnet45") { anthropicPayloadTest("claude-sonnet-4-5") }
})