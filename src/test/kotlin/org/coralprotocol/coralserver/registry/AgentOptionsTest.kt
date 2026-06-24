package org.coralprotocol.coralserver.registry

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.decodeFromString
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
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
import org.coralprotocol.coralserver.dsl.*
import org.koin.test.inject

class AgentOptionsTest : CoralTest({
    test("testString") {
        val toml by inject<Toml>()
        val option = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "string"
            default = "test default value"
            secret = true
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

        option.shouldBeInstanceOf<PolymorphicAgentOption.String>()
        option.default.shouldNotBeNull().shouldBeEqual("test default value")

        option.secret.shouldBeTrue()
        option.required.shouldBeTrue()
        option.base64.shouldBeTrue()


        val display = option.display.shouldNotBeNull()
        display.label.shouldNotBeNull().shouldBeEqual("Test Option")
        display.description.shouldNotBeNull().shouldBeEqual("A test option")
        display.group.shouldNotBeNull().shouldBeEqual("Test Group")
        display.multiline.shouldBeFalse()

        val validation = option.validation.shouldNotBeNull()
        validation.variants.shouldNotBeNull().shouldBeEqual(listOf("test1", "test2"))
        validation.minLength.shouldNotBeNull().shouldBeEqual(1)
        validation.maxLength.shouldNotBeNull().shouldBeEqual(100)
    }

    fun <OptionType, ValueType, BackingType> testNumeric(
        builder: (
            value: BackingType,
            block: NumericAgentOptionBuilder<BackingType, OptionType, *>.() -> Unit
        ) -> AgentOptionWithValue<OptionType, ValueType, BackingType>,
        min: BackingType,
        max: BackingType
    ) where OptionType : PolymorphicAgentOption<ValueType, BackingType>,
            ValueType : PolymorphicAgentOptionValue<BackingType>,
            BackingType : Comparable<BackingType> {
        val toml by inject<Toml>()
        val json by inject<Json>()

        fun recode(option: AgentOptionWithValue<OptionType, ValueType, BackingType>) {
            val optionJson = json.encodeToString(AgentOptionSerializer(), option.option)
            json.decodeFromString(AgentOptionSerializer(), optionJson).shouldBeEqual(option.option)

            val optionToml = toml.encodeToString(AgentOptionSerializer(), option.option)
            toml.decodeFromString(AgentOptionSerializer(), optionToml).shouldBeEqual(option.option)

            val valueJson = json.encodeToString(AgentOptionValueSerializer(), option.value)
            json.decodeFromString(AgentOptionValueSerializer(), valueJson).shouldBeEqual(option.value)

            val valueToml = toml.encodeToString(AgentOptionValueSerializer(), option.value)
            toml.decodeFromString(AgentOptionValueSerializer(), valueToml).shouldBeEqual(option.value)
        }

        recode(builder(min) {
            default = min
        })

        recode(builder(max) {
            default = max
        })
    }

    test("testI8") { testNumeric(::byteOptionWithValue, Byte.MIN_VALUE, Byte.MAX_VALUE) }
    test("testI16") { testNumeric(::shortOptionWithValue, Short.MIN_VALUE, Short.MAX_VALUE) }
    test("testI32") { testNumeric(::intOptionWithValue, Int.MIN_VALUE, Int.MAX_VALUE) }
    test("testI64") { testNumeric(::longOptionWithValue, Long.MIN_VALUE, Long.MAX_VALUE) }
    test("testU8") { testNumeric(::unsignedByteOptionWithValue, UByte.MIN_VALUE, UByte.MAX_VALUE) }
    test("testU16") { testNumeric(::unsignedShortOptionWithValue, UShort.MIN_VALUE, UShort.MAX_VALUE) }
    test("testU32") { testNumeric(::unsignedIntOptionWithValue, UInt.MIN_VALUE, UInt.MAX_VALUE) }
    test("testF32") { testNumeric(::floatOptionWithValue, Float.MIN_VALUE, Float.MAX_VALUE) }

    // toml currently unable to deserialize min value double
    test("testF64") { testNumeric(::doubleOptionWithValue, -1.0, Double.MAX_VALUE) }

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

        number.shouldBeInstanceOf<PolymorphicAgentOption.IntList>()

        shouldNotThrowAny {
            number.withValue(PolymorphicAgentOptionValue.IntList(listOf(10, 20, 30))).validateValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(PolymorphicAgentOptionValue.IntList(listOf(1000, 0))).validateValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(PolymorphicAgentOptionValue.IntList(listOf(40, 50, 60))).validateValue()
        }
    }

    test("testValidateString") {
        val toml by inject<Toml>()
        val string = toml.decodeFromString(
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

        string.shouldBeInstanceOf<PolymorphicAgentOption.String>()

        shouldNotThrowAny {
            string.withValue(PolymorphicAgentOptionValue.String("test@test.com")).validateValue()
        }
        shouldThrow<AgentOptionValidationException> {
            string.withValue(PolymorphicAgentOptionValue.String("not an email address")).validateValue()
        }
        shouldThrow<AgentOptionValidationException> {
            string.withValue(PolymorphicAgentOptionValue.String("a@a.se")).validateValue()
        }
        shouldThrow<AgentOptionValidationException> {
            string.withValue(PolymorphicAgentOptionValue.String("bad@email.com")).validateValue()
        }
    }

    test("testValidateStringList") {
        val toml by inject<Toml>()
        val stringList = toml.decodeFromString(
            AgentOptionSerializer(),
            """
            type = "list[string]"
            description = "Email test"

            [validation]
            regex = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$"
            """
        )

        stringList.shouldBeInstanceOf<PolymorphicAgentOption.StringList>()

        shouldNotThrowAny {
            stringList.withValue(
                PolymorphicAgentOptionValue.StringList(
                    listOf(
                        "test@test.com",
                        "a@a.se",
                        "good@email.com"
                    )
                )
            )
                .validateValue()
        }
        shouldThrow<AgentOptionValidationException> {
            stringList.withValue(PolymorphicAgentOptionValue.StringList(listOf("bad-email.com", "good@email.com")))
                .validateValue()
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
            ).validateValue()
        }
        shouldThrow<AgentOptionValidationException> {
            blob.withValue(
                PolymorphicAgentOptionValue.Blob.fromBytes(
                    ByteArray(1.mebibytes.inWholeBytes.toInt() + 1)
                )
            ).validateValue()
        }
        shouldThrow<AgentOptionValidationException> {
            blob.withValue(
                PolymorphicAgentOptionValue.Blob.fromBytes(
                    ByteArray(0)
                )
            ).validateValue()
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

    test("testIntegralGenericNarrowing") {
        // integers
        PolymorphicAgentOption.Byte(42).isIntegral().shouldBeTrue()
        PolymorphicAgentOption.Short(1000).isIntegral().shouldBeTrue()
        PolymorphicAgentOption.Int(123456).isIntegral().shouldBeTrue()
        PolymorphicAgentOption.Long(9876543210L).isIntegral().shouldBeTrue()
        PolymorphicAgentOption.UByte(255u).isIntegral().shouldBeTrue()
        PolymorphicAgentOption.UShort(65535u).isIntegral().shouldBeTrue()
        PolymorphicAgentOption.UInt(4294967295u).isIntegral().shouldBeTrue()
        PolymorphicAgentOption.ULong("18446744073709551615").isIntegral().shouldBeTrue()

        // floats (not integers)
        PolymorphicAgentOption.Float(3.14f).isIntegral().shouldBeFalse()
        PolymorphicAgentOption.Double(2.718281828459045).isIntegral().shouldBeFalse()

        // definitely not integers
        PolymorphicAgentOption.String("hello world").isIntegral().shouldBeFalse()
    }
})