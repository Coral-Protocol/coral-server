@file:OptIn(InternalSerializationApi::class)

package org.coralprotocol.coralserver.serialization

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.toml.TomlPropertySource
import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.buildTomlTable
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.equals.shouldBeEqual
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.saket.bytesize.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.util.ByteSizeDecoder
import org.coralprotocol.coralserver.util.ByteSizeSerializer
import org.koin.test.inject
import kotlin.math.E
import kotlin.math.PI

private data class SizeTest(
    val unit: String, val number: Number, val function: (Number) -> ByteSize
) {
    val byteSize = function(number)
}

class ByteSizeSerializationTest : CoralTest({
    val testSizes = listOf(
        SizeTest("KiB", PI) { it.kibibytes },
        SizeTest("MiB", -PI) { it.mebibytes },
        SizeTest("GiB", E) { it.gibibytes },
        SizeTest("KB", -PI) { it.kilobytes },
        SizeTest("MB", PI) { it.megabytes },
        SizeTest("GB", -E) { it.gigabytes },
        SizeTest("B", Long.MAX_VALUE) { it.binaryBytes },
    )

    test("testJsonSerialization") {
        val json by inject<Json>()
        for (test in testSizes) {
            shouldNotThrowAny { json.encodeToString(ByteSizeSerializer(), test.byteSize) }

            // string decode
            json.decodeFromString(ByteSizeSerializer(), "\"${test.number} ${test.unit}\"").shouldBeEqual(test.byteSize)

            // object decode
            json.decodeFromJsonElement(ByteSizeSerializer(), buildJsonObject {
                put("size", test.number)
                put("unit", test.unit)
            }).shouldBeEqual(test.byteSize)
        }
    }

    test("testTomlSerialization") {
        @Serializable
        data class Wrapper(@Serializable(with = ByteSizeSerializer::class) val size: ByteSize)

        val toml by inject<Toml>()
        for (test in testSizes) {

            shouldNotThrowAny { toml.encodeToString(Wrapper(test.byteSize)) }

            // string decode
            toml.decodeFromString<Wrapper>("size = \"${test.number} ${test.unit}\"").size.shouldBeEqual(test.byteSize)

            // object decode
            toml.decodeFromTomlElement(ByteSizeSerializer(), buildTomlTable {
                element(
                    "size", when (test.number) {
                        is Long -> TomlLiteral(test.number)
                        is Double -> TomlLiteral(test.number)
                        else -> throw IllegalArgumentException("Unsupported number type ${test.number::class.simpleName}")
                    }
                )
                element("unit", TomlLiteral(test.unit))
            }).shouldBeEqual(test.byteSize)
        }
    }

    test("testHopliteSerialization") {
        data class Wrapper(val size: ByteSize)

        for (test in testSizes) {
            // string decode
            ConfigLoaderBuilder.default().addDecoder(ByteSizeDecoder())
                .addSource(TomlPropertySource("size = \"${test.number} ${test.unit}\""))
                .build()
                .loadConfigOrThrow<Wrapper>().size.shouldBeEqual(test.byteSize)

            // object decode
            ConfigLoaderBuilder.default().addDecoder(ByteSizeDecoder()).addSource(
                TomlPropertySource(
                    """
                    [size]
                    size = ${test.number}
                    unit = "${test.unit}"
                    """.trimIndent()
                )
            ).build().loadConfigOrThrow<Wrapper>().size.shouldBeEqual(test.byteSize)
        }
    }
})