package org.coralprotocol.coralserver.session


import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeModelProvider
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeString
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest

class PrototypeRuntimeTest : CoralTest({
    val openaiApiKey = System.getenv("CORAL_TEST_OPENAI_API_KEY")
    suspend fun openaiPayloadTest(modelName: String) {
        multiAgentPayloadTest(
            PrototypeModelProvider.OpenAI(
                PrototypeString.Inline(openaiApiKey),
                PrototypeString.Inline(modelName)
            )
        )
    }

    test("testMultiAgentPayload").config(
        enabled = openaiApiKey != null,
        invocations = 10
    ) {
        openaiPayloadTest("gpt-4.1-nano")
    }
})