package org.coralprotocol.coralserver.llm

import io.kotest.matchers.collections.shouldNotBeEmpty
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeClient
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat
import org.coralprotocol.coralserver.llmproxy.LlmProxyService
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest

class CloudTest : CoralTest({
    System.getenv("CORAL_TEST_CLOUD_API_KEY")?.let {
        val cloudConfigs = LlmProxyService.buildCoralCloudProviders(it)
        cloudConfigs.shouldNotBeEmpty()

        for (cloudConfig in cloudConfigs) {
            for (model in cloudConfig.models) {
                test("testCloud${cloudConfig.format}[$model]") {
                    multiAgentPayloadTest(
                        configuration = cloudConfig,
                        client = when (cloudConfig.format) {
                            LlmProviderFormat.Anthropic -> PrototypeClient.ANTHROPIC
                            LlmProviderFormat.OpenAI -> PrototypeClient.OPEN_AI
                        },
                        model = model
                    )
                }
            }
        }
    }
})