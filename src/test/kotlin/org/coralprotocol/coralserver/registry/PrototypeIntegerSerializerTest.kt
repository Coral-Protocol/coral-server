@file:OptIn(InternalSerializationApi::class)

package org.coralprotocol.coralserver.registry

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.registry.MAXIMUM_SUPPORTED_AGENT_VERSION
import org.coralprotocol.coralserver.agent.registry.RegistryException
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionWithValue
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
            is AgentOptionValue.Byte -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Byte>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.Byte(
                            option,
                            agentOptionValue
                        )
                    )
                ).toByte().shouldBeEqual(agentOptionValue.value)
            }

            is AgentOptionValue.Int -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Int>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.Int(
                            option,
                            agentOptionValue
                        )
                    )
                ).toInt().shouldBeEqual(agentOptionValue.value)
            }

            is AgentOptionValue.Long -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Long>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.Long(
                            option,
                            agentOptionValue
                        )
                    )
                ).shouldBeEqual(agentOptionValue.value)
            }

            is AgentOptionValue.Short -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.Short>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.Short(
                            option,
                            agentOptionValue
                        )
                    )
                ).toShort().shouldBeEqual(agentOptionValue.value)
            }

            is AgentOptionValue.UByte -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.UByte>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.UByte(
                            option,
                            agentOptionValue
                        )
                    )
                ).toUByte().shouldBeEqual(agentOptionValue.value)
            }

            is AgentOptionValue.UInt -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.UInt>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.UInt(
                            option,
                            agentOptionValue
                        )
                    )
                ).toUInt().shouldBeEqual(agentOptionValue.value)
            }

            is AgentOptionValue.ULong -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.ULong>()

                agent.runtimes.prototypeRuntime.shouldNotBeNull().iterationCount.resolve(
                    mapOf(
                        "ITERATIONS" to AgentOptionWithValue.ULong(
                            option,
                            agentOptionValue
                        )
                    )
                ).toULong().shouldBeEqual(agentOptionValue.value.toULong())
            }

            is AgentOptionValue.UShort -> {
                val option = agent.options["ITERATIONS"].shouldNotBeNull().shouldBeInstanceOf<AgentOption.UShort>()

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
    test("testOptionSerializationByte") { testAgentOptionSerialization(AgentOptionValue.Byte(Byte.MAX_VALUE)) }
    test("testOptionSerializationInt") { testAgentOptionSerialization(AgentOptionValue.Int(Int.MIN_VALUE)) }
    test("testOptionSerializationLong") { testAgentOptionSerialization(AgentOptionValue.Long(Long.MIN_VALUE)) }
    test("testOptionSerializationShort") { testAgentOptionSerialization(AgentOptionValue.Short(Short.MIN_VALUE)) }

    // unsigned
    test("testOptionSerializationUByte") { testAgentOptionSerialization(AgentOptionValue.UByte(UByte.MAX_VALUE)) }
    test("testOptionSerializationUInt") { testAgentOptionSerialization(AgentOptionValue.UInt(UInt.MAX_VALUE)) }
    test("testOptionSerializationUShort") { testAgentOptionSerialization(AgentOptionValue.UShort(UShort.MAX_VALUE)) }
    test("testOptionSerializationULong") { testAgentOptionSerialization(AgentOptionValue.ULong(ULong.MAX_VALUE.toString())) }

    // unsupported
    test("testOptionSerializationFloat") {
        shouldThrow<RegistryException> {
            testAgentOptionSerialization(
                AgentOptionValue.Float(100.0f)
            )
        }
    }

    test("testOptionSerializationDouble") {
        shouldThrow<RegistryException> {
            testAgentOptionSerialization(
                AgentOptionValue.Double(100.0)
            )
        }
    }

    test("testOptionSerializationString") {
        shouldThrow<RegistryException> {
            testAgentOptionSerialization(
                AgentOptionValue.String("200")
            )
        }
    }
})