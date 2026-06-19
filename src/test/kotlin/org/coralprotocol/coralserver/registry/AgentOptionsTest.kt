package org.coralprotocol.coralserver.registry

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.decodeFromString
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.saket.bytesize.mebibytes
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.exceptions.AgentOptionValidationException
import org.coralprotocol.coralserver.agent.registry.option.*
import org.koin.test.inject
import kotlin.reflect.KClass

class AgentOptionsTest : CoralTest({
    test("testString") {
        val toml by inject<Toml>()
        val option = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "string"
            secret = true
            default = "test default value"
            required = true
            base64 = true
        
            [display]
            label = "Test Option"
            description = "A test option"
            group = "Test Group"
            multiline = false
        
            [validation]
            variants = ["test1", "test2"]
            min_length = 1
            max_length = 100
        """.trimIndent()
        )


        option.required.shouldBeTrue()
        option.shouldBeInstanceOf<PolymorphicAgentOption.String>()
        option.default.shouldNotBeNull().shouldBeEqual("test default value")
        option.base64.shouldBeTrue()
        option.secret.shouldBeTrue()
    }

    test("testNumeric") {
        val toml by inject<Toml>()

        data class TestCase(
            val typeName: String,
            val `class`: KClass<*>,
            val defaultValue: AgentOptionValue,
        )

        val tests = listOf(
            TestCase("i8", PolymorphicAgentOption.Byte::class, PolymorphicAgentOptionValue.Byte(Byte.MIN_VALUE)),
            TestCase("i16", PolymorphicAgentOption.Short::class, PolymorphicAgentOptionValue.Short(Short.MIN_VALUE)),
            TestCase("i32", PolymorphicAgentOption.Int::class, PolymorphicAgentOptionValue.Int(Int.MIN_VALUE)),
            TestCase(
                "i64",
                PolymorphicAgentOption.Long::class,
                PolymorphicAgentOptionValue.Long(Long.MIN_VALUE)
            ),
            TestCase("u8", PolymorphicAgentOption.UByte::class, PolymorphicAgentOptionValue.UByte(UByte.MAX_VALUE)),
            TestCase("u16", PolymorphicAgentOption.UShort::class, PolymorphicAgentOptionValue.UShort(UShort.MAX_VALUE)),
            TestCase("u32", PolymorphicAgentOption.UInt::class, PolymorphicAgentOptionValue.UInt(UInt.MAX_VALUE)),
            TestCase(
                "u64",
                PolymorphicAgentOption.ULong::class,
                PolymorphicAgentOptionValue.ULong(ULong.MAX_VALUE.toString())
            ),
            TestCase("f32", PolymorphicAgentOption.Float::class, PolymorphicAgentOptionValue.Float(1.0f)),
            TestCase("f64", PolymorphicAgentOption.Double::class, PolymorphicAgentOptionValue.Double(1.0)),

            TestCase(
                "list[i8]", PolymorphicAgentOption.ByteList::class, PolymorphicAgentOptionValue.ByteList(
                    listOf(Byte.MIN_VALUE, Byte.MAX_VALUE)
                )
            ),
            TestCase(
                "list[i16]", PolymorphicAgentOption.ShortList::class, PolymorphicAgentOptionValue.ShortList(
                    listOf(Short.MIN_VALUE, Short.MAX_VALUE)
                )
            ),
            TestCase(
                "list[i32]", PolymorphicAgentOption.IntList::class, PolymorphicAgentOptionValue.IntList(
                    listOf(Int.MIN_VALUE, Int.MAX_VALUE)
                )
            ),
            TestCase(
                "list[i64]", PolymorphicAgentOption.LongList::class, PolymorphicAgentOptionValue.LongList(
                    listOf(
                        Long.MIN_VALUE,
                        Long.MAX_VALUE
                    )
                )
            ),
            TestCase(
                "list[u8]", PolymorphicAgentOption.UByteList::class, PolymorphicAgentOptionValue.UByteList(
                    listOf(UByte.MIN_VALUE, UByte.MAX_VALUE)
                )
            ),
            TestCase(
                "list[u16]", PolymorphicAgentOption.UShortList::class, PolymorphicAgentOptionValue.UShortList(
                    listOf(UShort.MIN_VALUE, UShort.MAX_VALUE)
                )
            ),
            TestCase(
                "list[u32]", PolymorphicAgentOption.UIntList::class, PolymorphicAgentOptionValue.UIntList(
                    listOf(UInt.MIN_VALUE, UInt.MAX_VALUE)
                )
            ),
            TestCase(
                "list[u64]", PolymorphicAgentOption.ULongList::class, PolymorphicAgentOptionValue.ULongList(
                    listOf(ULong.MIN_VALUE.toString(), ULong.MAX_VALUE.toString())
                )
            ),
            TestCase(
                "list[f32]", PolymorphicAgentOption.FloatList::class, PolymorphicAgentOptionValue.FloatList(
                    listOf(-1.0f, 1.0f)
                )
            ),
            TestCase(
                "list[f64]", PolymorphicAgentOption.DoubleList::class, PolymorphicAgentOptionValue.DoubleList(
                    listOf(-1.0, 1.0)
                )
            )
        )

        for (test in tests) {
            val defaultStr = if (test.typeName.startsWith("list")) {
                "[${test.defaultValue.asEnvVarValue()}]"
            } else {
                test.defaultValue.asEnvVarValue()
            }

            val option = toml.decodeFromString(
                AgentOptionSerializer(),
                """
                type = "${test.typeName}"
                default = $defaultStr
                """
            )

            test.`class`.isInstance(option).shouldBeTrue()
            option.compareTypeWithValue(test.defaultValue).shouldBeTrue()
            option.defaultAsValue().shouldNotBeNull().shouldBeEqual(test.defaultValue)
        }
    }

    test("testValidateNumber") {
        val toml by inject<Toml>()
        val number = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "i32"
            description = "A test number"

            [validation]
            min = 10
            max = 100
            variants = [50, 9, 101]
            """
        )

        number.shouldBeInstanceOf<PolymorphicAgentOption.Int>()
        shouldNotThrowAny { number.validation!!.require(50) }
        shouldThrow<AgentOptionValidationException> { number.validation!!.require(9) } // too low
        shouldThrow<AgentOptionValidationException> { number.validation!!.require(101) } // too high
        shouldThrow<AgentOptionValidationException> { number.validation!!.require(70) } // wrong variant
    }

    test("testValidateNumberList") {
        val toml by inject<Toml>()
        val number = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "list[i32]"
            description = "A test number"
    
            [validation]
            min = 10
            max = 100
            variants = [10, 20, 30]
            """
        )

        shouldNotThrowAny {
            number.withValue(PolymorphicAgentOptionValue.IntList(listOf(10, 20, 30))).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(PolymorphicAgentOptionValue.IntList(listOf(1000, 0))).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(PolymorphicAgentOptionValue.IntList(listOf(40, 50, 60))).requireValue()
        }
    }

    test("testValidateString") {
        val toml by inject<Toml>()
        val number = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "string"
            description = "Email test"

            [validation]
            min_length = 10
            max_length = 30
            regex = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$"
            variants = ["test@test.com", "not an email address", "a@a.se"]
            """
        )

        shouldNotThrowAny {
            number.withValue(PolymorphicAgentOptionValue.String("test@test.com")).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(PolymorphicAgentOptionValue.String("not an email address")).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(PolymorphicAgentOptionValue.String("a@a.se")).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(PolymorphicAgentOptionValue.String("bad@email.com")).requireValue()
        }
    }

    test("testValidateStringList") {
        val toml by inject<Toml>()
        val number = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "list[string]"
            description = "Email test"

            [validation]
            regex = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$"
            """
        )

        shouldNotThrowAny {
            number.withValue(
                PolymorphicAgentOptionValue.StringList(
                    listOf(
                        "test@test.com",
                        "a@a.se",
                        "good@email.com"
                    )
                )
            )
                .requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(PolymorphicAgentOptionValue.StringList(listOf("bad-email.com", "good@email.com")))
                .requireValue()
        }
    }

    test("testValidateBlob") {
        val toml by inject<Toml>()
        val blob = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "blob"
            description = "Blob test"

            [validation]
            min_size = { size = 1.01, unit = "kB" }
            max_size = { size = 1.00, unit = "MiB" }
            """
        )

        blob.shouldBeInstanceOf<PolymorphicAgentOption.Blob>()
        shouldNotThrowAny {
            blob.withValue(
                PolymorphicAgentOptionValue.Blob.fromBytes(
                    ByteArray(1.mebibytes.inWholeBytes.toInt())
                )
            ).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            blob.withValue(
                PolymorphicAgentOptionValue.Blob.fromBytes(
                    ByteArray(1.mebibytes.inWholeBytes.toInt() + 1)
                )
            ).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            blob.withValue(
                PolymorphicAgentOptionValue.Blob.fromBytes(
                    ByteArray(0)
                )
            ).requireValue()
        }
    }

    // bug fix: partial validation table was not deserializable
    test("testPartialNumericValidation") {
        val toml by inject<Toml>()
        val number = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "i32"
            description = "A test number"

            [validation]
            max = 100
            """
        )

        number.shouldBeInstanceOf<PolymorphicAgentOption.Int>()
        repeat(100) { shouldNotThrowAny { number.validation!!.require(it) } }
        shouldThrow<AgentOptionValidationException> { number.validation!!.require(101) } // too high
    }

    test("testValidateStringU64") {
        val toml by inject<Toml>()
        val number = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "u64"
            description = "A test number"

            [validation]
            min = "1"
            max = "${ULong.MAX_VALUE - 1u}"
            """
        )

        number.shouldBeInstanceOf<PolymorphicAgentOption.ULong>()
        shouldNotThrowAny { number.validation!!.require(50UL) }
        shouldThrow<AgentOptionValidationException> { number.validation!!.require(0UL) } // too low
        shouldThrow<AgentOptionValidationException> { number.validation!!.require(ULong.MAX_VALUE) } // too high
    }

    test("testAgentValueSerialization") {
        val json by inject<Json>()
        val toml by inject<Toml>()

        val values = listOf(
            PolymorphicAgentOptionValue.String("hello world"),
            PolymorphicAgentOptionValue.StringList(listOf("foo", "bar", "baz")),
            PolymorphicAgentOptionValue.Blob.fromBytes(byteArrayOf(0x01, 0x02, 0x03)),
            PolymorphicAgentOptionValue.BlobList.fromByteList(
                listOf(
                    byteArrayOf(0xAA.toByte()),
                    byteArrayOf(0xBB.toByte())
                )
            ),
            PolymorphicAgentOptionValue.Boolean(true),
            PolymorphicAgentOptionValue.Byte(42),
            PolymorphicAgentOptionValue.ByteList(listOf(1, -1, 127, -128)),
            PolymorphicAgentOptionValue.Short(1000),
            PolymorphicAgentOptionValue.ShortList(listOf(100, -100, 32767)),
            PolymorphicAgentOptionValue.Int(123456),
            PolymorphicAgentOptionValue.IntList(listOf(0, -1, Int.MAX_VALUE)),
            PolymorphicAgentOptionValue.Long(9876543210L),
            PolymorphicAgentOptionValue.LongList(listOf(0L, Long.MIN_VALUE, Long.MAX_VALUE)),
            PolymorphicAgentOptionValue.UByte(255u),
            PolymorphicAgentOptionValue.UByteList(listOf(0u, 128u, 255u)),
            PolymorphicAgentOptionValue.UShort(65535u),
            PolymorphicAgentOptionValue.UShortList(listOf(0u, 1000u, 65535u)),
            PolymorphicAgentOptionValue.UInt(4294967295u),
            PolymorphicAgentOptionValue.UIntList(listOf(0u, 1u, UInt.MAX_VALUE)),
            PolymorphicAgentOptionValue.ULong("18446744073709551615"),
            PolymorphicAgentOptionValue.ULongList(listOf("0", "1", "18446744073709551615")),
            PolymorphicAgentOptionValue.Float(3.14f),
            PolymorphicAgentOptionValue.FloatList(listOf(0f, -1.5f, Float.MAX_VALUE)),
            PolymorphicAgentOptionValue.Double(2.718281828459045),
            PolymorphicAgentOptionValue.DoubleList(listOf(0.0, -1.0, Double.MAX_VALUE)),
        )

        @Serializable
        data class Wrapped(val value: AgentOptionValue)

        for (value: AgentOptionValue in values) {
            val wrapped = Wrapped(value)

            val jsonEncoded = json.encodeToString(wrapped)
            json.decodeFromString<Wrapped>(jsonEncoded).shouldBeEqual(wrapped)

            val tomlEncoded = toml.encodeToString(wrapped)
            toml.decodeFromString<Wrapped>(tomlEncoded).shouldBeEqual(wrapped)
        }
    }
})