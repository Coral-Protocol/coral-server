package org.coralprotocol.coralserver.registry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.AGENT_LLM_PROXY_NAME_LENGTH
import org.coralprotocol.coralserver.agent.registry.MAXIMUM_SUPPORTED_AGENT_VERSION
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.registry.stringReferenceConstants
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
        prototypeRuntime.proxyName.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual("MAIN")

        prototypeRuntime.prompts.system.base.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual("base system prompt")
        prototypeRuntime.prompts.system.extra.shouldBeInstanceOf<PrototypeString.Option>().name.shouldBeEqual("EXTRA_SYSTEM_PROMPT")

        prototypeRuntime.prompts.loop.initial.base.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual("base initial loop prompt")
        prototypeRuntime.prompts.loop.initial.extra.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual(
            File("src/test/resources/prototype/PROMPT.MD").readText()
        )
    }

    test("testPrototypeStringUrlReference") {
        val uuid = UUID.randomUUID().toString().substring(0, AGENT_LLM_PROXY_NAME_LENGTH.last)
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
                
                [runtimes.prototype]
                proxy = { type = "url", url = "$urlPath" }
            """.trimIndent()
        )

        agent.runtimes.prototypeRuntime.shouldNotBeNull()
            .proxyName.shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual(uuid)
    }

    test("testJsonPrototypeStrings") {
        val json by inject<Json>()

        // discriminated inline
        var value = UUID.randomUUID().toString()
        json.decodeFromString(
            PrototypeString.serializer(),
            """
                {
                  "type": "inline",
                  "value": "$value"
                }
            """.trimIndent()
        ).shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual(value)

        // discriminated option
        value = UUID.randomUUID().toString()
        json.decodeFromString(
            PrototypeString.serializer(),
            """
                {
                  "type": "option",
                  "name": "$value"
                }
            """.trimIndent()
        ).shouldBeInstanceOf<PrototypeString.Option>().name.shouldBeEqual(value)

        // string literal
        value = UUID.randomUUID().toString()
        json.decodeFromString(PrototypeString.serializer(), "\"$value\"")
            .shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual(value)

        // string constant reference
        // discriminated inline
        var (constantName, constantValue) = stringReferenceConstants.entries.first()
        json.decodeFromString(
            PrototypeString.serializer(),
            """
                {
                  "type": "constant",
                  "name": "$constantName"
                }
            """.trimIndent()
        ).shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual(constantValue)

        // url reference (should not be allowed)
        shouldThrow<SerializationException> {
            json.decodeFromString(
                PrototypeString.serializer(),
                """
                    {
                      "type": "url",
                      "url": "https://google.se"
                    }
                """.trimIndent()
            ).shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual(constantValue)
        }

        // file reference (should not be allowed)
        shouldThrow<SerializationException> {
            val file = tempfile("test.txt")
            json.decodeFromString(
                PrototypeString.serializer(),
                """
                    {
                      "type": "file",
                      "file": "$file"
                    }
                """.trimIndent()
            ).shouldBeInstanceOf<PrototypeString.Inline>().value.shouldBeEqual(constantValue)
        }
    }
})