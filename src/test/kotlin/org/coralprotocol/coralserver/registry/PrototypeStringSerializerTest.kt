package org.coralprotocol.coralserver.registry

import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.MAXIMUM_SUPPORTED_AGENT_VERSION
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeModelProvider
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeString
import org.koin.test.inject
import java.io.File
import java.util.*

class PrototypeStringSerializerTest : CoralTest({
    val urlPath = "string"
    fun serveString(text: String) {
        val application by inject<Application>()

        application.routing {
            get(urlPath) {
                call.respondText(text)
            }
        }
    }

    test("testFullPrototypeString") {
        val agent = UnresolvedRegistryAgent.resolveFromFile(
            File("src/test/resources/prototype/coral-agent.toml")
        )

        val prototypeRuntime = agent.runtimes.prototypeRuntime.shouldNotBeNull()
        val modelProvider = prototypeRuntime.modelProvider.shouldBeInstanceOf<PrototypeModelProvider.OpenAI>()
        modelProvider.key.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual("key here")

        prototypeRuntime.prompts.system.base.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual("base system prompt")
        prototypeRuntime.prompts.system.extra.shouldBeInstanceOf<PrototypeString.Option>().name.shouldBeEqual("EXTRA_SYSTEM_PROMPT")

        prototypeRuntime.prompts.loop.initial.base.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual("base initial loop prompt")
        prototypeRuntime.prompts.loop.initial.extra.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual(
            File("src/test/resources/prototype/PROMPT.MD").readText()
        )
    }

    test("testPrototypeStringUrlReference") {
        val uuid = UUID.randomUUID().toString()
        serveString(uuid)

        val agent = UnresolvedRegistryAgent.resolveFromString(
            """
                edition = $MAXIMUM_SUPPORTED_AGENT_VERSION
                
                [agent]
                name = "prototype-url-reference"
                version = "0.0.1"
                description = "test"
                summary = "test"
                readme = "test"
                license = { type = "spdx", expression = "MIT" }
                
                [runtimes.prototype.model]
                provider = "openai"
                name = "gpt-3.5-turbo"
                key = { type = "url", url = "$urlPath" }
            """.trimIndent()
        )

        val prototypeRuntime = agent.runtimes.prototypeRuntime.shouldNotBeNull()
        val modelProvider = prototypeRuntime.modelProvider.shouldBeInstanceOf<PrototypeModelProvider.OpenAI>()
        modelProvider.key.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual(uuid)
    }
})