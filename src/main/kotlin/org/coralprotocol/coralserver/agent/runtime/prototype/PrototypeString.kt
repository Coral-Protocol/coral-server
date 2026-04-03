@file:OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime.prototype

import dev.eav.tomlkt.*
import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.agent.exceptions.PrototypeRuntimeException
import org.coralprotocol.coralserver.agent.registry.RegistryAgentStringSerializer
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionValue
import org.coralprotocol.coralserver.agent.registry.option.value
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import kotlin.reflect.full.findAnnotation

@Serializable(with = PrototypeStringSerializer::class)
@TomlClassDiscriminator("type")
@JsonClassDiscriminator("type")
sealed interface PrototypeString {
    fun resolve(executionContext: SessionAgentExecutionContext): String

    @Serializable
    @SerialName("inline")
    data class Inline(val value: String) : PrototypeString {
        override fun resolve(executionContext: SessionAgentExecutionContext): String = value
    }

    @Serializable
    @SerialName("option")
    data class Option(val name: String) : PrototypeString {
        override fun resolve(executionContext: SessionAgentExecutionContext): String {
            val option = executionContext.agent.graphAgent.options[name]
                ?: throw PrototypeRuntimeException.BadOption("option \"$name\" wasn't found")

            val optionValue = option.value()
            if (optionValue !is AgentOptionValue.String)
                throw PrototypeRuntimeException.BadOption("option \"$name\" must have type=\"string\"")

            return optionValue.value
        }
    }

    @Serializable
    @SerialName("composed_string")
    data class ComposedString(
        val parts: List<PrototypeString>,
        val separator: String = ""
    ) : PrototypeString {
        override fun resolve(executionContext: SessionAgentExecutionContext): String =
            parts.joinToString(separator) { it.resolve(executionContext) }
    }

    @Serializable
    @SerialName("composed_url")
    data class ComposedUrl(
        val base: String,
        val parts: List<UrlPart>,
    ) : PrototypeString {
        override fun resolve(executionContext: SessionAgentExecutionContext): String {
            val builder = URLBuilder(base)
            for (part in parts) {
                when (part) {
                    is UrlPart.Path -> builder.appendPathSegments(part.value.resolve(executionContext))
                    is UrlPart.QueryParameter -> builder.appendPathSegments(part.value.resolve(executionContext))
                }
            }

            return builder.buildString()
        }
    }
}

@Serializable
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface UrlPart {
    @Serializable
    @SerialName("query_parameter")
    data class QueryParameter(val name: String, val value: PrototypeString) : UrlPart

    @Serializable
    @SerialName("path_segment")
    data class Path(val value: PrototypeString) : UrlPart
}

/**
 * The prototype string serializer allows for convenient deserialization syntaxes in TOML.  It does not affect JSOM
 * deserialization.  It does not support TOML serialization.
 *
 * Accepted TOML syntaxes:
 *
 * # Inline strings
 *
 * ```toml
 * key = { type = "inline", value = "inline string value" }
 * ```
 *
 * ```toml
 * key = { type = "string", value = "inline string value" }
 * ```
 *
 * ```toml
 * key = "inline string value"
 * ```
 *
 * # Option
 *
 * ```toml
 * key = { type = "option", name = "MY_OPTION_NAME" }
 * ```
 *
 * # Reference
 *
 * ```toml
 * [key]
 * type = "file"
 * path = "/my/file/path.txt"
 * encoding = "UTF-8" # optional, defaults to UTF-8
 * base64 = false # optional, defaults to false
 * ```
 *
 * ```toml
 * [key]
 * type = "url"
 * path = "https://my-server.com/my-file.txt"
 * encoding = "UTF-8" # optional, defaults to UTF-8
 * base64 = false # optional, defaults to false
 * ```
 */
object PrototypeStringSerializer : KSerializer<PrototypeString> {
    private val inlineSerializer = PrototypeString.Inline.serializer()
    private val optionSerializer = PrototypeString.Option.serializer()
    private val composedStringSerializer = PrototypeString.ComposedString.serializer()
    private val composedUrlSerializer = PrototypeString.ComposedUrl.serializer()

    private val prototypeStringDiscriminator = run {
        val tomlDiscriminator = PrototypeString::class
            .findAnnotation<TomlClassDiscriminator>()?.discriminator
            ?: "type"

        val jsonDiscriminator = PrototypeString::class
            .findAnnotation<TomlClassDiscriminator>()?.discriminator
            ?: "type"

        require(tomlDiscriminator == jsonDiscriminator)
        tomlDiscriminator
    }

    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        "PrototypeString",
        SerialKind.CONTEXTUAL
    )

    override fun serialize(encoder: Encoder, value: PrototypeString) {
        when (encoder) {
            is JsonEncoder -> {
                val (type, element) = when (value) {
                    is PrototypeString.Inline -> Pair(
                        inlineSerializer.descriptor.serialName,
                        encoder.json.encodeToJsonElement(inlineSerializer, value)
                    )

                    is PrototypeString.Option -> Pair(
                        optionSerializer.descriptor.serialName,
                        encoder.json.encodeToJsonElement(optionSerializer, value)
                    )

                    is PrototypeString.ComposedString -> Pair(
                        composedStringSerializer.descriptor.serialName,
                        encoder.json.encodeToJsonElement(composedStringSerializer, value)
                    )

                    is PrototypeString.ComposedUrl -> Pair(
                        composedUrlSerializer.descriptor.serialName,
                        encoder.json.encodeToJsonElement(composedUrlSerializer, value)
                    )
                }

                encoder.encodeJsonElement(JsonObject(mapOf(prototypeStringDiscriminator to JsonPrimitive(type)) + element as JsonObject))
            }

            else -> throw SerializationException("Unsupported encoder: ${encoder::class}")
        }
    }

    override fun deserialize(decoder: Decoder): PrototypeString {
        return when (decoder) {

            // json should only support plain deserialization of discriminated option/inline subtypes
            is JsonDecoder -> {
                val jsonObject = decoder.decodeJsonElement() as JsonObject

                when (val type = jsonObject[prototypeStringDiscriminator]?.jsonPrimitive?.content) {
                    inlineSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                        inlineSerializer,
                        JsonObject(jsonObject.filterKeys { it != prototypeStringDiscriminator })
                    )

                    optionSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                        optionSerializer,
                        JsonObject(jsonObject.filterKeys { it != prototypeStringDiscriminator })
                    )

                    composedStringSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                        composedStringSerializer,
                        JsonObject(jsonObject.filterKeys { it != prototypeStringDiscriminator })
                    )

                    composedUrlSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                        composedUrlSerializer,
                        JsonObject(jsonObject.filterKeys { it != prototypeStringDiscriminator })
                    )

                    else -> throw SerializationException("Unknown type: $type")
                }
            }

            // TOML deserialization should allow inline strings to represent as string literals and should also
            // support PotentialStringReference deserialization
            is TomlDecoder -> {
                val tomlElement = decoder.decodeTomlElement()
                try {
                    PrototypeString.Inline(RegistryAgentStringSerializer().deserialize(decoder))
                } catch (_: IllegalArgumentException) {

                    when (val type =
                        tomlElement.asTomlTable()[prototypeStringDiscriminator]?.asTomlLiteral()?.content) {
                        inlineSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                            inlineSerializer,
                            TomlTable(tomlElement.filterKeys { it != prototypeStringDiscriminator })
                        )

                        optionSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                            optionSerializer,
                            TomlTable(tomlElement.filterKeys { it != prototypeStringDiscriminator })
                        )

                        composedStringSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                            composedStringSerializer,
                            TomlTable(tomlElement.filterKeys { it != prototypeStringDiscriminator })
                        )

                        composedUrlSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                            composedUrlSerializer,
                            TomlTable(tomlElement.filterKeys { it != prototypeStringDiscriminator })
                        )

                        else -> throw SerializationException("Unknown type: $type")
                    }
                }
            }

            else -> throw SerializationException("Unsupported decoder: ${decoder::class}")
        }
    }
}