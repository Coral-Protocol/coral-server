package org.coralprotocol.coralserver.llm

import io.kotest.core.test.config.DefaultTestConfig
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest

/**
 * This test should be run sparingly!
 */
class OpenAITest : CoralTest({
    defaultTestConfig = DefaultTestConfig(
        enabledIf = ::hasOpenAIProxy
    )

    suspend fun openaiPayloadTest(modelName: String) {
        multiAgentPayloadTest(openAIProxy!!, modelName)
    }

    test("testGpt41") { openaiPayloadTest("gpt-4.1") }
    test("testGpt41Mini") { openaiPayloadTest("gpt-4.1-mini") }
    test("testGpt41Nano") { openaiPayloadTest("gpt-4.1-nano") }
    test("testGpt4o") { openaiPayloadTest("gpt-4o") }
    test("testGpt4oMini") { openaiPayloadTest("gpt-4o-mini") }
    test("testGpt5") { openaiPayloadTest("gpt-5") }
    test("testGpt5Codex") { openaiPayloadTest("gpt-5-codex") }
    test("testGpt5Mini") { openaiPayloadTest("gpt-5-mini") }
    test("testGpt5Nano") { openaiPayloadTest("gpt-5-nano") }
    test("testGpt5Pro") { openaiPayloadTest("gpt-5-pro") }
    test("testGpt51") { openaiPayloadTest("gpt-5.1") }
    test("testGpt51Codex") { openaiPayloadTest("gpt-5.1-codex") }
    test("testGpt51CodexMax") { openaiPayloadTest("gpt-5.1-codex-max") }
    test("testGpt52") { openaiPayloadTest("gpt-5.2") }
    test("testGpt52Codex") { openaiPayloadTest("gpt-5.2-codex") }
    test("testGpt52Pro") { openaiPayloadTest("gpt-5.2-pro") }
    test("testGpt53Codex") { openaiPayloadTest("gpt-5.3-codex") }
    test("testGpt54") { openaiPayloadTest("gpt-5.4") }
    test("testGpt54Pro") { openaiPayloadTest("gpt-5.4-pro") }
    test("testO1") { openaiPayloadTest("o1") }
    test("testO3") { openaiPayloadTest("o3") }
    test("testO3Mini") { openaiPayloadTest("o3-mini") }
    test("testO4Mini") { openaiPayloadTest("o4-mini") }
} )