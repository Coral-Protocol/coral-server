package org.coralprotocol.coralserver.llm

import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeClient
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat
import org.coralprotocol.coralserver.llmproxy.LlmProxyService
import org.coralprotocol.coralserver.utils.multiAgentHandshakeTest
import kotlin.time.Duration.Companion.minutes

class CloudTest : CoralTest({
    val cloudProviders = System.getenv("CORAL_TEST_CLOUD_API_KEY")?.let { LlmProxyService.buildCoralCloudProviders(it) }
    val openAI = cloudProviders?.firstOrNull { it.format == LlmProviderFormat.OpenAI }
    val deepSeek = cloudProviders?.firstOrNull { it.format == LlmProviderFormat.DeepSeek }

    test("testCloudOpenAI[o3-mini]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "o3-mini",
            timeout = 3.minutes
        )
    }

    test("testCloudOpenAI[o4-mini]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "o4-mini",
            timeout = 3.minutes
        )
    }

    test("testCloudOpenAI[gpt-4.1]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-4.1",
            timeout = 3.minutes
        )
    }

    test("testCloudOpenAI[gpt-4.1-mini]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-4.1-mini",
            timeout = 3.minutes
        )
    }

    // nano models fail to perform to benchmark
    test("testCloudOpenAI[gpt-4.1-nano]").config(enabledIf = { openAI != null }, enabled = false) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-4.1-nano",
            timeout = 3.minutes
        )
    }

    test("testCloudOpenAI[gpt-5]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5",
            timeout = 3.minutes
        )
    }

    test("testCloudOpenAI[gpt-5.1]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5.1",
            timeout = 3.minutes
        )
    }

    // not supported by Koog
    test("testCloudOpenAI[gpt-5.1-codex-mini]").config(enabledIf = { openAI != null }, enabled = false) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5.1-codex-mini",
            timeout = 3.minutes
        )
    }

    test("testCloudOpenAI[gpt-5.2]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5.2",
            timeout = 3.minutes
        )
    }

    // requires responses API
    test("testCloudOpenAI[gpt-5.2-codex]").config(enabledIf = { openAI != null }, enabled = false) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5.2-codex",
            timeout = 3.minutes
        )
    }

    // requires responses API
    test("testCloudOpenAI[gpt-5.3-codex]").config(enabledIf = { openAI != null }, enabled = false) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5.3-codex",
            timeout = 3.minutes
        )
    }

    test("testCloudOpenAI[gpt-5.4]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5.4",
            timeout = 3.minutes
        )
    }

    // nano models fail to perform to benchmark
    test("testCloudOpenAI[gpt-5.4-nano]").config(enabledIf = { openAI != null }, enabled = false) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5.4-nano",
            timeout = 3.minutes
        )
    }

    test("testCloudOpenAI[gpt-5.4-mini]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5.4-mini",
            timeout = 3.minutes
        )
    }

    test("testCloudOpenAI[gpt-5.5]").config(enabledIf = { openAI != null }) {
        multiAgentHandshakeTest(
            configuration = openAI!!,
            client = PrototypeClient.OpenAI(),
            model = "gpt-5.5",
            timeout = 3.minutes
        )
    }

    test("testCloudDeepSeek[deepseek-v4-flash]").config(
        enabledIf = { deepSeek != null }
    ) {
        multiAgentHandshakeTest(
            configuration = deepSeek!!,
            client = PrototypeClient.DeepSeek(),
            model = "deepseek-v4-flash",
            timeout = 3.minutes
        )
    }

    test("testCloudDeepSeek[deepseek-v4-pro]").config(enabledIf = { deepSeek != null }) {
        multiAgentHandshakeTest(
            configuration = deepSeek!!,
            client = PrototypeClient.DeepSeek(),
            model = "deepseek-v4-pro",
            timeout = 3.minutes
        )
    }
})