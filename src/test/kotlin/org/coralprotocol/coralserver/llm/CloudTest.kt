package org.coralprotocol.coralserver.llm

import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeClient
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat
import org.coralprotocol.coralserver.llmproxy.LlmProxyService
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest

class CloudTest : CoralTest({
    val cloudApiKey: String? = System.getenv("CORAL_TEST_CLOUD_API_KEY")

    test("testSupportedCloudModels").config(enabled = cloudApiKey != null) {
        val cloudApiKey = cloudApiKey!!
        val cloudConfigs = LlmProxyService.buildCoralCloudProviders(cloudApiKey)
        cloudConfigs.shouldNotBeEmpty()

        for (cloudConfig in cloudConfigs) {
            { cloudConfig }.asClue {
                cloudConfig.models.shouldNotBeEmpty()
                for (model in cloudConfig.models) {
                    { model }.asClue {
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
    }
})