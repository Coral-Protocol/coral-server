package org.coralprotocol.coralserver.llm

import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest

/**
 * This test should be run sparingly!
 */
class AnthropicTest : CoralTest({
    suspend fun anthropicPayloadTest(modelName: String) {
        multiAgentPayloadTest(anthropicProxy!!, modelName)
    }

    test("testClaude3Haiku").config(enabledIf = ::hasAnthropicProxy) { anthropicPayloadTest("claude-3-haiku") }
    test("testClaudeHaiku45").config(enabledIf = ::hasAnthropicProxy) { anthropicPayloadTest("claude-haiku-4-5") }
    test("testClaudeOpus40").config(enabledIf = ::hasAnthropicProxy) { anthropicPayloadTest("claude-opus-4-0") }
    test("testClaudeOpus41").config(enabledIf = ::hasAnthropicProxy) { anthropicPayloadTest("claude-opus-4-1") }
    test("testClaudeOpus45").config(enabledIf = ::hasAnthropicProxy) { anthropicPayloadTest("claude-opus-4-5") }
    test("testClaudeOpus46").config(enabledIf = ::hasAnthropicProxy) { anthropicPayloadTest("claude-opus-4-6") }
    test("testClaudeSonnet40").config(enabledIf = ::hasAnthropicProxy) { anthropicPayloadTest("claude-sonnet-4-0") }
    test("testClaudeSonnet45").config(enabledIf = ::hasAnthropicProxy) { anthropicPayloadTest("claude-sonnet-4-5") }
})