package org.coralprotocol.coralserver.llm

import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeClient
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat
import org.coralprotocol.coralserver.llmproxy.LlmProxyService
import org.coralprotocol.coralserver.utils.multiAgentPayloadTest
import kotlin.time.Duration.Companion.minutes

class CloudTest : CoralTest({

    val cloudProviders = System.getenv("CORAL_TEST_CLOUD_API_KEY")?.let { LlmProxyService.buildCoralCloudProviders(it) }
    val openAI = cloudProviders?.firstOrNull { it.format == LlmProviderFormat.OpenAI }
    val deepSeek = cloudProviders?.firstOrNull { it.format == LlmProviderFormat.DeepSeek }

    test("testCloudOpenAI[o3-mini]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "o3-mini")
    }

    test("testCloudOpenAI[o4-mini]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "o4-mini")
    }

    test("testCloudOpenAI[gpt-4.1]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-4.1")
    }

    test("testCloudOpenAI[gpt-4.1-mini]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-4.1-mini")
    }

    // nano models fail to perform to benchmark
//    test("testCloudOpenAI[gpt-4.1-nano]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
//        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-4.1-nano")
//    }

    test("testCloudOpenAI[gpt-5]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5")
    }

    test("testCloudOpenAI[gpt-5.1]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5.1")
    }

    // not supported by Koog
//    test("testCloudOpenAI[gpt-5.1-codex-mini]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
//        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5.1-codex-mini")
//    }

    test("testCloudOpenAI[gpt-5.2]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5.2")
    }

    // requires responses API
//    test("testCloudOpenAI[gpt-5.2-codex]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
//        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5.2-codex")
//    }

    // requires responses API
//    test("testCloudOpenAI[gpt-5.3-codex]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
//        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5.3-codex")
//    }

    test("testCloudOpenAI[gpt-5.4]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5.4")
    }

    // nano models fail to perform to benchmark
//    test("testCloudOpenAI[gpt-5.4-nano]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
//        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5.4-nano")
//    }

    test("testCloudOpenAI[gpt-5.4-mini]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5.4-mini")
    }

    test("testCloudOpenAI[gpt-5.5]").config(enabledIf = { openAI != null }, invocationTimeout = 1.minutes) {
        multiAgentPayloadTest(configuration = openAI!!, client = PrototypeClient.OPEN_AI, model = "gpt-5.5")
    }

    test("testCloudDeepSeek[deepseek-v4-flash]").config(
        enabledIf = { deepSeek != null },
        invocationTimeout = 3.minutes
    ) {
        multiAgentPayloadTest(
            configuration = deepSeek!!,
            client = PrototypeClient.DEEPSEEK,
            model = "deepseek-v4-flash"
        )
    }

    test("testCloudDeepSeek[deepseek-v4-pro]").config(enabledIf = { deepSeek != null }, invocationTimeout = 3.minutes) {
        multiAgentPayloadTest(configuration = deepSeek!!, client = PrototypeClient.DEEPSEEK, model = "deepseek-v4-pro")
    }
})