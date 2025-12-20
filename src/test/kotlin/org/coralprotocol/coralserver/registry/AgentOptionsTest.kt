package org.coralprotocol.coralserver.registry

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import nl.adaptivity.xmlutil.core.impl.multiplatform.name
import org.coralprotocol.coralserver.agent.exceptions.AgentOptionValidationException
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.asEnvVarValue
import org.coralprotocol.coralserver.agent.registry.option.compareTypeWithValue
import org.coralprotocol.coralserver.agent.registry.option.defaultAsValue
import org.coralprotocol.coralserver.agent.registry.option.requireValue
import org.coralprotocol.coralserver.agent.registry.option.withValue
import org.coralprotocol.coralserver.config.toml
import org.coralprotocol.coralserver.util.ByteUnitSizes
import org.coralprotocol.coralserver.util.toByteCount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class AgentOptionsTest : FunSpec({
    test("testString") {
        val option = toml.decodeFromString(
            AgentOption.serializer(), """
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
        option.shouldBeInstanceOf<AgentOption.String>()
        option.default.shouldNotBeNull().shouldBeEqual("test default value")
        option.base64.shouldBeTrue()
        option.secret.shouldBeTrue()
    }

    test("testNumeric") {
        data class TestCase(
            val typeName: String,
            val className: String,
            val defaultValue: AgentOptionValue,
        )

        val tests = listOf(
            TestCase("i8", AgentOption.Byte::class.name, AgentOptionValue.Byte(Byte.MIN_VALUE)),
            TestCase("i16", AgentOption.Short::class.name, AgentOptionValue.Short(Short.MIN_VALUE)),
            TestCase("i32", AgentOption.Int::class.name, AgentOptionValue.Int(Int.MIN_VALUE)),
            TestCase(
                "i64",
                AgentOption.Long::class.name,
                AgentOptionValue.Long(-1)
            ), // Bug with Long.MIN_VALUE, see https://github.com/Peanuuutz/tomlkt/issues/81
            TestCase("u8", AgentOption.UByte::class.name, AgentOptionValue.UByte(UByte.MAX_VALUE)),
            TestCase("u16", AgentOption.UShort::class.name, AgentOptionValue.UShort(UShort.MAX_VALUE)),
            TestCase("u32", AgentOption.UInt::class.name, AgentOptionValue.UInt(UInt.MAX_VALUE)),
            TestCase("u64", AgentOption.ULong::class.name, AgentOptionValue.ULong(ULong.MAX_VALUE.toString())),
            TestCase("f32", AgentOption.Float::class.name, AgentOptionValue.Float(1.0f)),
            TestCase("f64", AgentOption.Double::class.name, AgentOptionValue.Double(1.0)),

            TestCase(
                "list[i8]", AgentOption.ByteList::class.name, AgentOptionValue.ByteList(
                    listOf(Byte.MIN_VALUE, Byte.MAX_VALUE)
                )
            ),
            TestCase(
                "list[i16]", AgentOption.ShortList::class.name, AgentOptionValue.ShortList(
                    listOf(Short.MIN_VALUE, Short.MAX_VALUE)
                )
            ),
            TestCase(
                "list[i32]", AgentOption.IntList::class.name, AgentOptionValue.IntList(
                    listOf(Int.MIN_VALUE, Int.MAX_VALUE)
                )
            ),
            TestCase(
                "list[i64]", AgentOption.LongList::class.name, AgentOptionValue.LongList(
                    listOf(
                        -1,
                        Long.MAX_VALUE
                    ) // Bug with Long.MIN_VALUE, see https://github.com/Peanuuutz/tomlkt/issues/81
                )
            ),
            TestCase(
                "list[u8]", AgentOption.UByteList::class.name, AgentOptionValue.UByteList(
                    listOf(UByte.MIN_VALUE, UByte.MAX_VALUE)
                )
            ),
            TestCase(
                "list[u16]", AgentOption.UShortList::class.name, AgentOptionValue.UShortList(
                    listOf(UShort.MIN_VALUE, UShort.MAX_VALUE)
                )
            ),
            TestCase(
                "list[u32]", AgentOption.UIntList::class.name, AgentOptionValue.UIntList(
                    listOf(UInt.MIN_VALUE, UInt.MAX_VALUE)
                )
            ),
            TestCase(
                "list[u64]", AgentOption.ULongList::class.name, AgentOptionValue.ULongList(
                    listOf(ULong.MIN_VALUE.toString(), ULong.MAX_VALUE.toString())
                )
            ),
            TestCase(
                "list[f32]", AgentOption.FloatList::class.name, AgentOptionValue.FloatList(
                    listOf(-1.0f, 1.0f)
                )
            ),
            TestCase(
                "list[f64]", AgentOption.DoubleList::class.name, AgentOptionValue.DoubleList(
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
                AgentOption.serializer(), """
                type = "${test.typeName}"
                default = $defaultStr
            """
            )
            option.javaClass.name.shouldBeEqual(test.className)
            option.compareTypeWithValue(test.defaultValue).shouldBeTrue()
            option.defaultAsValue().shouldNotBeNull().shouldBeEqual(test.defaultValue)
        }
    }

    test("testFirstEdition") {
        val number = toml.decodeFromString(
            AgentOption.serializer(), """
            type = "number"
            description = "A test number"
            default = 123
        """.trimIndent()
        )
        number.shouldBeInstanceOf<AgentOption.Number>()
        number.default.shouldNotBeNull().shouldBeEqual(123.0)

        val string = toml.decodeFromString(
            AgentOption.serializer(), """
            type = "string"
            description = "A test number"
            default = "test default value"
        """.trimIndent()
        )
        string.shouldBeInstanceOf<AgentOption.String>()
        string.default.shouldNotBeNull().shouldBeEqual("test default value")

        val secret = toml.decodeFromString(
            AgentOption.serializer(), """
            type = "secret"
            description = "A test secret"
            default = "secret secret"
        """.trimIndent()
        )
        secret.shouldBeInstanceOf<AgentOption.Secret>()
        secret.default.shouldNotBeNull().shouldBeEqual("secret secret")
    }

    test("testValidateNumber") {
        val number = toml.decodeFromString(
            AgentOption.serializer(), """
            type = "i32"
            description = "A test number"
        
            [validation]
            min = 10
            max = 100
            variants = [50, 9, 101]
        """
        )

        number.shouldBeInstanceOf<AgentOption.Int>()
        shouldNotThrowAny { number.validation!!.require(50) }
        shouldThrow<AgentOptionValidationException> { number.validation!!.require(9) } // too low
        shouldThrow<AgentOptionValidationException> { number.validation!!.require(101) } // too high
        shouldThrow<AgentOptionValidationException> { number.validation!!.require(70) } // wrong variant
    }

    test("testValidateNumberList") {
        val number = toml.decodeFromString(
            AgentOption.serializer(), """
            type = "list[i32]"
            description = "A test number"
        
            [validation]
            min = 10
            max = 100
            variants = [10, 20, 30]
        """
        )

        shouldNotThrowAny {
            number.withValue(AgentOptionValue.IntList(listOf(10, 20, 30))).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(AgentOptionValue.IntList(listOf(1000, 0))).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(AgentOptionValue.IntList(listOf(40, 50, 60))).requireValue()
        }
    }

    test("testValidateString") {
        val number = toml.decodeFromString(
            AgentOption.serializer(), """
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
            number.withValue(AgentOptionValue.String("test@test.com")).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(AgentOptionValue.String("not an email address")).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(AgentOptionValue.String("a@a.se")).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(AgentOptionValue.String("bad@email.com")).requireValue()
        }
    }

    test("testValidateStringList") {
        val number = toml.decodeFromString(
            AgentOption.serializer(), """
            type = "list[string]"
            description = "Email test"
        
            [validation]
            regex = "^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$"
        """
        )

        shouldNotThrowAny {
            number.withValue(AgentOptionValue.StringList(listOf("test@test.com", "a@a.se", "good@email.com")))
                .requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            number.withValue(AgentOptionValue.StringList(listOf("bad-email.com", "good@email.com"))).requireValue()
        }
    }

    test("testValidateBlob") {
        val blob = toml.decodeFromString(
            AgentOption.serializer(), """
            type = "blob"
            description = "Blob test"
        
            [validation]
            min_size = { size = 1.01, unit = "kB" }
            max_size = { size = 1.00, unit = "MiB" }
        """
        )

        blob.shouldBeInstanceOf<AgentOption.Blob>()
        shouldNotThrowAny {
            blob.withValue(
                AgentOptionValue.Blob(
                    ByteArray(ByteUnitSizes.MEBIBYTE.toByteCount(1.0).toInt())
                )
            ).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            blob.withValue(
                AgentOptionValue.Blob(
                    ByteArray(ByteUnitSizes.MEBIBYTE.toByteCount(1.0).toInt() + 1)
                )
            ).requireValue()
        }
        shouldThrow<AgentOptionValidationException> {
            blob.withValue(
                AgentOptionValue.Blob(
                    ByteArray(0)
                )
            ).requireValue()
        }
    }
})