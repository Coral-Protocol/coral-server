package org.coralprotocol.coralserver.llm

import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeModelProvider
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeString
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest

/**
 * This test should be run sparingly!
 */
class AnthropicTest : CoralTest({
    val anthropicApiKey = System.getenv("CORAL_TEST_ANTHROPIC_API_KEY")

    suspend fun anthropicPayloadTest(modelName: String) {
        multiAgentPayloadTest(
            PrototypeModelProvider.Anthropic(
                PrototypeString.Inline(anthropicApiKey),
                PrototypeString.Inline(modelName)
            )
        )
    }

    test("testClaude3Haiku").config(enabled = anthropicApiKey != null) { anthropicPayloadTest("claude-3-haiku") }
    test("testClaudeHaiku45").config(enabled = anthropicApiKey != null) { anthropicPayloadTest("claude-haiku-4-5") }
    test("testClaudeOpus40").config(enabled = anthropicApiKey != null) { anthropicPayloadTest("claude-opus-4-0") }
    test("testClaudeOpus41").config(enabled = anthropicApiKey != null) { anthropicPayloadTest("claude-opus-4-1") }
    test("testClaudeOpus45").config(enabled = anthropicApiKey != null) { anthropicPayloadTest("claude-opus-4-5") }
    test("testClaudeOpus46").config(enabled = anthropicApiKey != null) { anthropicPayloadTest("claude-opus-4-6") }
    test("testClaudeSonnet40").config(enabled = anthropicApiKey != null) { anthropicPayloadTest("claude-sonnet-4-0") }
    test("testClaudeSonnet45").config(enabled = anthropicApiKey != null) { anthropicPayloadTest("claude-sonnet-4-5") }
})