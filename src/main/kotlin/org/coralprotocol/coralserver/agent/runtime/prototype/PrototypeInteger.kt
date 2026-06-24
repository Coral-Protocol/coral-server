@file:OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime.prototype

import dev.eav.tomlkt.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.agent.exceptions.PrototypeRuntimeException
import org.coralprotocol.coralserver.agent.registry.option.AnyAgentOptionWithValue
import org.coralprotocol.coralserver.agent.registry.option.PolymorphicAgentOptionValue
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import kotlin.reflect.full.findAnnotation

@Serializable(with = PrototypeIntegerSerializer::class)
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed class PrototypeInteger {
    fun resolve(executionContext: SessionAgentExecutionContext): Long = resolve(executionContext.graphAgent.options)
    abstract fun resolve(agentOptions: Map<String, AnyAgentOptionWithValue> = mapOf()): Long

    @Serializable
    @SerialName("inline")
    data class Inline(val value: Long) : PrototypeInteger() {
        override fun resolve(agentOptions: Map<String, AnyAgentOptionWithValue>): Long = value
    }

    @Serializable
    @SerialName("option")
    data class Option(val name: String) : PrototypeInteger() {
        override fun resolve(agentOptions: Map<String, AnyAgentOptionWithValue>): Long {
            val option = agentOptions[name]
                ?: throw PrototypeRuntimeException.BadOption("option \"$name\" wasn't found")
            // TODO: generify interface providing toLong()
            return when (val optionValue = option.value) {
                is PolymorphicAgentOptionValue.Int -> optionValue.value.toLong()
                is PolymorphicAgentOptionValue.Long -> optionValue.value
                is PolymorphicAgentOptionValue.Short -> optionValue.value.toLong()
                is PolymorphicAgentOptionValue.UByte -> optionValue.value.toLong()
                is PolymorphicAgentOptionValue.Byte -> optionValue.value.toLong()
                is PolymorphicAgentOptionValue.UInt -> optionValue.value.toLong()
                is PolymorphicAgentOptionValue.ULong -> optionValue.value.toULong().toLong()
                is PolymorphicAgentOptionValue.UShort -> optionValue.value.toLong()
                else -> throw PrototypeRuntimeException.BadOption("option \"$name\" must have an integral type")
            }
        }
    }
}

object PrototypeIntegerSerializer : KSerializer<PrototypeInteger> {
    private val inlineSerializer = PrototypeInteger.Inline.serializer()
    private val optionSerializer = PrototypeInteger.Option.serializer()

    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        "PrototypeInteger",
        SerialKind.CONTEXTUAL
    )

    private val prototypeIntegerDiscriminator = run {
        val tomlDiscriminator = PrototypeInteger::class
            .findAnnotation<TomlClassDiscriminator>()?.discriminator
            ?: "type"

        val jsonDiscriminator = PrototypeInteger::class
            .findAnnotation<JsonClassDiscriminator>()?.discriminator
            ?: "type"

        require(tomlDiscriminator == jsonDiscriminator)
        tomlDiscriminator
    }

    override fun serialize(encoder: Encoder, value: PrototypeInteger) {
        when (encoder) {
            is JsonEncoder -> {
                val (type, element) = when (value) {
                    is PrototypeInteger.Inline -> Pair(
                        inlineSerializer.descriptor.serialName,
                        encoder.json.encodeToJsonElement(inlineSerializer, value)
                    )

                    is PrototypeInteger.Option -> Pair(
                        optionSerializer.descriptor.serialName,
                        encoder.json.encodeToJsonElement(optionSerializer, value)
                    )
                }

                encoder.encodeJsonElement(JsonObject(mapOf(prototypeIntegerDiscriminator to JsonPrimitive(type)) + element as JsonObject))
            }

            else -> throw SerializationException("Unsupported encoder: ${encoder::class}")
        }
    }

    override fun deserialize(decoder: Decoder): PrototypeInteger {
        return when (decoder) {
            // JSON should only support plain deserialization of discriminated option/inline subtypes
            is JsonDecoder -> {
                val jsonObject = decoder.decodeJsonElement() as JsonObject

                when (val type = jsonObject[prototypeIntegerDiscriminator]?.jsonPrimitive?.content) {
                    inlineSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                        inlineSerializer,
                        JsonObject(jsonObject.filterKeys { it != prototypeIntegerDiscriminator })
                    )

                    optionSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                        optionSerializer,
                        JsonObject(jsonObject.filterKeys { it != prototypeIntegerDiscriminator })
                    )

                    else -> throw SerializationException("Unknown type: $type")
                }
            }

            // TOML should allow discriminated types or integer literals
            is TomlDecoder -> {
                val tomlElement = decoder.decodeTomlElement()
                if (tomlElement is TomlLiteral && tomlElement.type == TomlLiteral.Type.Integer) {
                    PrototypeInteger.Inline(tomlElement.content.toLong())
                } else {
                    when (val type =
                        tomlElement.asTomlTable()[prototypeIntegerDiscriminator]?.asTomlLiteral()?.content) {
                        inlineSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                            inlineSerializer,
                            TomlTable(tomlElement.filterKeys { it != prototypeIntegerDiscriminator })
                        )

                        optionSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                            optionSerializer,
                            TomlTable(tomlElement.filterKeys { it != prototypeIntegerDiscriminator })
                        )

                        else -> throw SerializationException("Unknown type: $type")
                    }
                }
            }

            else -> throw SerializationException("Unsupported decoder: ${decoder::class}")
        }
    }
}