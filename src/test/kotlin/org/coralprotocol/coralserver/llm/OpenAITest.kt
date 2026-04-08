package org.coralprotocol.coralserver.llm

import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeModelProvider
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeString
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest

/**
 * This test should be run sparingly!
 */
class OpenAITest : CoralTest({
    val openaiApiKey = System.getenv("CORAL_TEST_OPENAI_API_KEY")
    suspend fun openaiPayloadTest(modelName: String) {
        multiAgentPayloadTest(
            PrototypeModelProvider.OpenAI(
                PrototypeString.Inline(openaiApiKey),
                PrototypeString.Inline(modelName)
            )
        )
    }

    test("testGpt41").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-4.1") }
    test("testGpt41Mini").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-4.1-mini") }
    test("testGpt41Nano").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-4.1-nano") }
    test("testGpt4o").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-4o") }
    test("testGpt4oMini").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-4o-mini") }
    test("testGpt5").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5") }
    test("testGpt5Codex").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5-codex") }
    test("testGpt5Mini").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5-mini") }
    test("testGpt5Nano").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5-nano") }
    test("testGpt5Pro").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5-pro") }
    test("testGpt51").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5.1") }
    test("testGpt51Codex").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5.1-codex") }
    test("testGpt51CodexMax").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5.1-codex-max") }
    test("testGpt52").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5.2") }
    test("testGpt52Codex").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5.2-codex") }
    test("testGpt52Pro").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5.2-pro") }
    test("testGpt53Codex").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5.3-codex") }
    test("testGpt54").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5.4") }
    test("testGpt54Pro").config(enabled = openaiApiKey != null) { openaiPayloadTest("gpt-5.4-pro") }
    test("testO1").config(enabled = openaiApiKey != null) { openaiPayloadTest("o1") }
    test("testO3").config(enabled = openaiApiKey != null) { openaiPayloadTest("o3") }
    test("testO3Mini").config(enabled = openaiApiKey != null) { openaiPayloadTest("o3-mini") }
    test("testO4Mini").config(enabled = openaiApiKey != null) { openaiPayloadTest("o4-mini") }
})