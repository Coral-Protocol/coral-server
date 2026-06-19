@file:OptIn(InternalSerializationApi::class)

package org.coralprotocol.coralserver.registry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.MAXIMUM_SUPPORTED_AGENT_VERSION
import org.coralprotocol.coralserver.agent.registry.RegistryException
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.PolymorphicAgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
import org.coralprotocol.coralserver.agent.registry.option.PolymorphicAgentOption
import org.coralprotocol.coralserver.agent.runtime.prototype.PrototypeInteger

class PrototypeIntegerSerializerTest : CoralTest({
    val baseAgent = """
                edition = $MAXIMUM_SUPPORTED_AGENT_VERSION
                
                [agent]
                name = "test-inline-serialization"
                version = "0.0.1"
                description = "test"
                summary = "test"
                readme = "test"
                license = { type = "spdx", expression = "MIT" }
                
                [[llm.proxies]]
                name = "TEST"
                format.type = "OpenAI"
                
                [runtimes.prototype]
                proxy = "TEST"
            """.trimIndent()

    test("testInlineSerialization") {
        val inlineValue = 101L
        UnresolvedRegistryAgent.resolveFromString(
            """
                $baseAgent
                iterations = $inlineValue
            """.trimIndent()
        ).runtimes.prototypeRuntime.shouldNotBeNull().iterationCount
            .shouldBeInstanceOf<PrototypeInteger.Inline>().value.shouldBeEqual(inlineValue)

        UnresolvedRegistryAgent.resolveFromString(
            """
                $baseAgent
                iterations = { type = "inline", value = $inlineValue }
            """.trimIndent()
        ).runtimes.prototypeRuntime.shouldNotBeNull().iterationCount
            .shouldBeInstanceOf<PrototypeInteger.Inline>().value.shouldBeEqual(inlineValue)
    }

    fun testAgentOptionSerialization(agentOptionValue: AgentOptionValue) {
        val agent = UnresolvedRegistryAgent.resolveFromString(
            """
                $baseAgent
                iterations = { type = "option", name = "ITERATIONS" }
                
                [options.ITERATIONS]
                type = "${agentOptionValue::class.serializer().descriptor.serialName}"
            """.trimIndent()
        )

        when (agentOptionValue) {
            is PolymorphicAgentOptionValue.Byte -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.Byte>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.Byte(
                            option,
                            agentOptionValue
                        )
                    )
                ).toByte().shouldBeEqual(agentOptionValue.value)
            }

            is PolymorphicAgentOptionValue.Int -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.Int>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.Int(
                            option,
                            agentOptionValue
                        )
                    )
                ).toInt().shouldBeEqual(agentOptionValue.value)
            }

            is PolymorphicAgentOptionValue.Long -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.Long>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.Long(
                            option,
                            agentOptionValue
                        )
                    )
                ).shouldBeEqual(agentOptionValue.value)
            }

            is PolymorphicAgentOptionValue.Short -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.Short>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.Short(
                            option,
                            agentOptionValue
                        )
                    )
                ).toShort().shouldBeEqual(agentOptionValue.value)
            }

            is PolymorphicAgentOptionValue.UByte -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.UByte>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.UByte(
                            option,
                            agentOptionValue
                        )
                    )
                ).toUByte().shouldBeEqual(agentOptionValue.value)
            }

            is PolymorphicAgentOptionValue.UInt -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.UInt>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.UInt(
                            option,
                            agentOptionValue
                        )
                    )
                ).toUInt().shouldBeEqual(agentOptionValue.value)
            }

            is PolymorphicAgentOptionValue.ULong -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.ULong>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.ULong(
                            option,
                            agentOptionValue
                        )
                    )
                ).toULong().shouldBeEqual(agentOptionValue.value.toULong())
            }

            is PolymorphicAgentOptionValue.UShort -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<PolymorphicAgentOption.UShort>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.UShort(
                            option,
                            agentOptionValue
                        )
                    )
                ).toUShort().shouldBeEqual(agentOptionValue.value)
            }

            else -> {
                // registry agent validation should not allow other types
            }
        }
    }

    // signed
    test("testOptionSerializationByte") { testAgentOptionSerialization(PolymorphicAgentOptionValue.Byte(Byte.MAX_VALUE)) }
    test("testOptionSerializationInt") { testAgentOptionSerialization(PolymorphicAgentOptionValue.Int(Int.MIN_VALUE)) }
    test("testOptionSerializationLong") { testAgentOptionSerialization(PolymorphicAgentOptionValue.Long(Long.MIN_VALUE)) }
    test("testOptionSerializationShort") { testAgentOptionSerialization(PolymorphicAgentOptionValue.Short(Short.MIN_VALUE)) }

    // unsigned
    test("testOptionSerializationUByte") { testAgentOptionSerialization(PolymorphicAgentOptionValue.UByte(UByte.MAX_VALUE)) }
    test("testOptionSerializationUInt") { testAgentOptionSerialization(PolymorphicAgentOptionValue.UInt(UInt.MAX_VALUE)) }
    test("testOptionSerializationUShort") { testAgentOptionSerialization(PolymorphicAgentOptionValue.UShort(UShort.MAX_VALUE)) }
    test("testOptionSerializationULong") { testAgentOptionSerialization(PolymorphicAgentOptionValue.ULong(ULong.MAX_VALUE.toString())) }

    // unsupported
    test("testOptionSerializationFloat") {
        shouldThrow<RegistryException> {
            testAgentOptionSerialization(
                PolymorphicAgentOptionValue.Float(100.0f)
            )
        }
    }

    test("testOptionSerializationDouble") {
        shouldThrow<RegistryException> {
            testAgentOptionSerialization(
                PolymorphicAgentOptionValue.Double(100.0)
            )
        }
    }

    test("testOptionSerializationString") {
        shouldThrow<RegistryException> {
            testAgentOptionSerialization(
                PolymorphicAgentOptionValue.String("200")
            )
        }
    }
})