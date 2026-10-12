package org.coralprotocol.coralserver.registry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldHaveKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.charsets.isSupported
import kotlinx.serialization.SerializationException
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.MAXIMUM_SUPPORTED_AGENT_VERSION
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.registry.option.PolymorphicAgentOption
import org.coralprotocol.coralserver.agent.registry.stringReferenceConstants
import org.koin.test.inject
import java.io.File
import java.util.*
import kotlin.io.encoding.Base64
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
                license = { type = "spdx", expression = "MIT" }
                
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

        shouldThrow<SerializationException> {
            UnresolvedRegistryAgent.resolveFromString(
                """
                edition = $MAXIMUM_SUPPORTED_AGENT_VERSION
                
                [agent]
                name = "string-url-reference"
                version = "0.0.1"
                description = "test"
                summary = "test"
                license = { type = "spdx", expression = "MIT" }
                
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

        agent.readme.shouldBeEqual(File("src/test/resources/string-file-reference/windows-1251/README.MD").readText(charset("Windows-1251")))
    }

    test("testDisabledFileReferences") {
        shouldThrow<SerializationException> {
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
        val blobValue = Base64.encode(
            File("src/test/resources/string-file-reference/options/blob.txt").readBytes()
        )

        agent.options["STRING"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.String>().default.shouldNotBeNull()
            .shouldBeEqual(stringValue)

        agent.options["STRING_BASE64"].shouldNotBeNull()
            .shouldBeInstanceOf<PolymorphicAgentOption.String>().default.shouldNotBeNull()
            .shouldBeEqual(Base64.encode(stringValue.encodeToByteArray()))

        agent.options["BLOB"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.Blob>().default.shouldNotBeNull()
            .shouldBeEqual(blobValue)

        agent.options["STRING_LIST"].shouldNotBeNull()
            .shouldBeInstanceOf<PolymorphicAgentOption.StringList>().default.shouldNotBeNull()
            .shouldContainExactly(listOf(stringValue, stringValue))

        agent.options["BLOB_LIST"].shouldNotBeNull()
            .shouldBeInstanceOf<PolymorphicAgentOption.BlobList>().default.shouldNotBeNull()
            .shouldBeEqual(listOf(blobValue, blobValue))

        agent.options["STRING_NO_DEFAULT"].shouldNotBeNull()
            .shouldBeInstanceOf<PolymorphicAgentOption.String>().default.shouldBeNull()

        agent.options["BLOB_NO_DEFAULT"].shouldNotBeNull()
            .shouldBeInstanceOf<PolymorphicAgentOption.Blob>().default.shouldBeNull()
    }

    test("binaryBlobReferencesPreserveEveryByte") {
        val bytes = ByteArray(256) { it.toByte() }
        val file = File.createTempFile("coral-binary-reference-", ".bin")
        try {
            file.writeBytes(bytes)
            val application by inject<Application>()
            application.routing {
                get("binary-blob-reference") { call.respondBytes(bytes) }
            }
            for (reference in listOf(
                "{ type = \"file\", path = \"${file.invariantSeparatorsPath}\" }",
                "{ type = \"url\", url = \"binary-blob-reference\" }"
            )) {
                val agent = UnresolvedRegistryAgent.resolveFromString(
                    """
                    edition = $MAXIMUM_SUPPORTED_AGENT_VERSION
                    [agent]
                    name = "binary-reference"
                    version = "1.0.0"
                    description = "test"
                    summary = "test"
                    readme = "test"
                    license = { type = "spdx", expression = "MIT" }
                    [runtimes.docker]
                    image = "ubuntu"
                    [options.BINARY]
                    type = "blob"
                    default = $reference
                    [options.BINARIES]
                    type = "list[blob]"
                    default = [$reference]
                    """.trimIndent()
                )
                agent.options["BINARY"].shouldBeInstanceOf<PolymorphicAgentOption.Blob>()
                    .default.shouldNotBeNull().shouldBeEqual(Base64.encode(bytes))
                agent.options["BINARIES"].shouldBeInstanceOf<PolymorphicAgentOption.BlobList>()
                    .default.shouldNotBeNull().shouldContainExactly(listOf(Base64.encode(bytes)))
            }
        } finally {
            file.delete()
        }
    }

    test("lossyCharsetConversionsAreRejected") {
        val file = File.createTempFile("coral-malformed-reference-", ".bin")
        try {
            file.writeBytes(byteArrayOf(0xFF.toByte()))
            val path = file.invariantSeparatorsPath
            for ((option, error) in listOf(
                // Malformed input when transcoding a blob between charsets
                """
                type = "blob"
                default = { type = "file", path = "$path", output = { base64 = true, charset = "UTF-16" } }
                """ to "not valid UTF-8",
                // Malformed input when reading a plain string
                """
                type = "string"
                default = { type = "file", path = "$path" }
                """ to "not valid UTF-8",
                // Unmappable output characters
                """
                type = "string"
                default = { type = "string", value = "\u65e5\u672c", output = { base64 = true, charset = "ISO-8859-1" } }
                """ to "cannot be represented in ISO-8859-1"
            )) {
                shouldThrow<SerializationException> {
                    UnresolvedRegistryAgent.resolveFromString(
                        """
                        |edition = $MAXIMUM_SUPPORTED_AGENT_VERSION
                        |[agent]
                        |name = "lossy-reference"
                        |version = "1.0.0"
                        |description = "test"
                        |summary = "test"
                        |readme = "test"
                        |license = { type = "spdx", expression = "MIT" }
                        |[runtimes.docker]
                        |image = "ubuntu"
                        |[options.LOSSY]
                        |${option.trimIndent()}
                        """.trimMargin()
                    )
                }.message.shouldNotBeNull().shouldContain(error)
            }
        } finally {
            file.delete()
        }
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
                license = { type = "spdx", expression = "MIT" }
                
                [runtimes.docker]
                image = "ubuntu"
                
                ${constants.joinToString("\n") { it.first }}
            """.trimIndent()
        )

        stringReferenceConstants.forEach { (name, value) ->
            val option = agent.options.shouldHaveKey(name)[name].shouldNotBeNull()
            option.shouldBeInstanceOf<PolymorphicAgentOption.String>().default.shouldNotBeNull().shouldBeEqual(value)
        }
    }
})
