package org.coralprotocol.coralserver.registry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldHaveKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.isSupported
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.MAXIMUM_SUPPORTED_AGENT_VERSION
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.stringReferenceConstants
import org.koin.test.inject
import java.io.File
import java.util.*
import kotlin.text.Charsets

class RegistryAgentStringSerializerTest : CoralTest({
    val urlPath = "string"
    fun serveString(text: String) {
        val application by inject<Application>()

        application.routing {
            get(urlPath) {
                call.respondText(text)
            }
        }
    }

    test("testStringUrlReference") {
        val uuid = UUID.randomUUID().toString()
        serveString(uuid)

        val agent = UnresolvedRegistryAgent.resolveFromString(
            """
                edition = $MAXIMUM_SUPPORTED_AGENT_VERSION
                
                [agent]
                name = "string-url-reference"
                version = "0.0.1"
                description = "test"
                summary = "test"
                license = { type = "sdpx", expression = "MIT" }
                
                readme = { type = "url", url = "$urlPath" }
                
                [runtimes.docker]
                image = "ubuntu"
            """.trimIndent()
        )

        agent.readme.shouldBeEqual(uuid)
    }

    test("testDisabledUrlReferences") {
        val uuid = UUID.randomUUID().toString()
        serveString(uuid)

        shouldThrow<IllegalStateException> {
            UnresolvedRegistryAgent.resolveFromString(
                """
                edition = $MAXIMUM_SUPPORTED_AGENT_VERSION
                
                [agent]
                name = "string-url-reference"
                version = "0.0.1"
                description = "test"
                summary = "test"
                license = { type = "sdpx", expression = "MIT" }
                
                readme = { type = "url", url = "$urlPath" }
                
                [runtimes.docker]
                image = "ubuntu"
            """.trimIndent(), enableUrlReferences = false
            )
        }
    }

    test("testStringFileReferenceUtf8") {
        val agent = UnresolvedRegistryAgent.resolveFromFile(
            File("src/test/resources/string-file-reference/utf8/coral-agent.toml")
        )

        agent.readme.shouldBeEqual(File("src/test/resources/string-file-reference/utf8/README.MD").readText())
    }

    test("testStringFileReferenceWindows1251").config(enabled = Charsets.isSupported("Windows-1251")) {
        val agent = UnresolvedRegistryAgent.resolveFromFile(
            File("src/test/resources/string-file-reference/windows-1251/coral-agent.toml")
        )

        agent.readme.shouldBeEqual(File("src/test/resources/string-file-reference/windows-1251/README.MD").readText())
    }

    test("testDisabledFileReferences") {
        shouldThrow<IllegalStateException> {
            UnresolvedRegistryAgent.resolveFromFile(
                File("src/test/resources/string-file-reference/utf8/coral-agent.toml"),
                enableFileReferences = false
            )
        }
    }

    test("testOptionDefaults") {
        val agent = UnresolvedRegistryAgent.resolveFromFile(
            File("src/test/resources/string-file-reference/options/coral-agent.toml")
        )

        val stringValue = File("src/test/resources/string-file-reference/options/string.txt").readText()
        val blobValue = File("src/test/resources/string-file-reference/options/blob.txt").readText().encodeBase64()

        agent.options["STRING"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.String>().default.shouldNotBeNull()
            .shouldBeEqual(stringValue)

        agent.options["STRING_BASE64"].shouldNotBeNull()
            .shouldBeInstanceOf<AgentOption.String>().default.shouldNotBeNull()
            .shouldBeEqual(stringValue.encodeBase64())

        agent.options["BLOB"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Blob>().default.shouldNotBeNull()
            .shouldBeEqual(blobValue)

        agent.options["STRING_LIST"].shouldNotBeNull()
            .shouldBeInstanceOf<AgentOption.StringList>().default.shouldNotBeNull()
            .shouldContainExactly(listOf(stringValue, stringValue))

        agent.options["BLOB_LIST"].shouldNotBeNull()
            .shouldBeInstanceOf<AgentOption.BlobList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf(blobValue, blobValue))

        agent.options["STRING_NO_DEFAULT"].shouldNotBeNull()
            .shouldBeInstanceOf<AgentOption.String>().default.shouldBeNull()

        agent.options["BLOB_NO_DEFAULT"].shouldNotBeNull()
            .shouldBeInstanceOf<AgentOption.Blob>().default.shouldBeNull()

        println(agent)
    }

    test("testStringConstants") {
        val constants = stringReferenceConstants.map { (name, value) ->
            """
                [options.$name]
                type = "string"
                default = { type = "constant", name = "$name" }
            """ to value
        }
        
        val agent = UnresolvedRegistryAgent.resolveFromString(
            """
                edition = $MAXIMUM_SUPPORTED_AGENT_VERSION
                
                [agent]
                name = "string-url-reference"
                version = "0.0.1"
                description = "test"
                summary = "test"
                readme = "test"
                license = { type = "sdpx", expression = "MIT" }
                
                [runtimes.docker]
                image = "ubuntu"
                
                ${constants.joinToString("\n") { it.first }}
            """.trimIndent()
        )

        stringReferenceConstants.forEach { (name, value) ->
            val option = agent.options.shouldHaveKey(name)[name].shouldNotBeNull()
            option.shouldBeInstanceOf<AgentOption.String>().default.shouldNotBeNull().shouldBeEqual(value)
        }
    }
})