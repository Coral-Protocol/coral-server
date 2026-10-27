package org.coralprotocol.coralserver.registry

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import dev.eav.tomlkt.Toml
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.exceptions.PrototypeRuntimeException
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeClient
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeLlmCapability
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeLlmModel
import org.coralprotocol.coralserver.config.LlmProxyProviderConfig
import org.coralprotocol.coralserver.llmproxy.LlmProviderFormat
import org.coralprotocol.coralserver.llmproxy.LlmProxiedModel
import org.koin.test.inject

class PrototypeClientTest : CoralTest({
    val providerConfig = LlmProxyProviderConfig(
        name = "test",
        format = LlmProviderFormat.OpenAI,
        apiKey = "",
        baseUrl = "http://localhost"
    )

    test("testCustomModelToml") {
        val toml by inject<Toml>()
        val client = toml.decodeFromString(
            PrototypeClient.serializer(),
            """
            type = "openai"
            
            [custom_model]
            capabilities = ["completion", "tools", "tool_choice", "openai_chat_completions"]
            context_length = 16384
            max_output_tokens = 4096
            """.trimIndent()
        )

        val customModel = client.shouldBeInstanceOf<PrototypeClient.OpenAI>().customModel.shouldNotBeNull()
        customModel.capabilities.shouldContainExactly(
            PrototypeLlmCapability.COMPLETION,
            PrototypeLlmCapability.TOOLS,
            PrototypeLlmCapability.TOOL_CHOICE,
            PrototypeLlmCapability.OPENAI_CHAT_COMPLETIONS
        )
        customModel.contextLength.shouldNotBeNull().shouldBeEqual(16384)
        customModel.maxOutputTokens.shouldNotBeNull().shouldBeEqual(4096)
    }

    test("testCustomModelDefaults") {
        val json by inject<Json>()
        val client = json.decodeFromString(PrototypeClient.serializer(), """{"type":"openai","custom_model":{}}""")

        val customModel = client.shouldBeInstanceOf<PrototypeClient.OpenAI>().customModel.shouldNotBeNull()
        customModel.capabilities.shouldContainExactly(PrototypeLlmModel.DEFAULT_CAPABILITIES)
        customModel.contextLength.shouldBeNull()

        json.decodeFromString(PrototypeClient.serializer(), json.encodeToString(PrototypeClient.serializer(), client))
            .shouldBeEqual(client)
    }

    test("testCustomModelResolution") {
        val llmModel = PrototypeClient.OpenAI(customModel = PrototypeLlmModel(contextLength = 16384))
            .getLlmModel(LlmProxiedModel(providerConfig, "my-custom-model"))

        llmModel.id.shouldBeEqual("my-custom-model")
        llmModel.provider.shouldBeEqual(LLMProvider.OpenAI)
        llmModel.contextLength.shouldNotBeNull().shouldBeEqual(16384)
        llmModel.capabilities.shouldNotBeNull().shouldContainExactly(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.OpenAIEndpoint.Completions
        )
    }

    test("testAnthropicCustomModel") {
        val toml by inject<Toml>()
        val client = toml.decodeFromString(
            PrototypeClient.serializer(),
            """
            type = "anthropic"

            [custom_model]
            capabilities = ["completion", "tools", "tool_choice"]
            """.trimIndent()
        )

        val llmModel = client.shouldBeInstanceOf<PrototypeClient.Anthropic>()
            .getLlmModel(LlmProxiedModel(providerConfig, "my-custom-model"))

        llmModel.id.shouldBeEqual("my-custom-model")
        llmModel.provider.shouldBeEqual(LLMProvider.Anthropic)
        llmModel.capabilities.shouldNotBeNull().shouldContainExactly(
            LLMCapability.Completion,
            LLMCapability.Tools,
            LLMCapability.ToolChoice
        )
    }

    test("testAnthropicWithoutCustomModel") {
        val json by inject<Json>()
        json.decodeFromString(PrototypeClient.serializer(), """{"type":"anthropic"}""")
            .shouldBeInstanceOf<PrototypeClient.Anthropic>().customModel.shouldBeNull()
    }

    test("testUnknownModelWithoutCustomModel") {
        shouldThrow<PrototypeRuntimeException.BadModel> {
            PrototypeClient.OpenAI().getLlmModel(LlmProxiedModel(providerConfig, "my-custom-model"))
        }
    }
})
