@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import dev.eav.tomlkt.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.charsets.forName
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.agent.runtime.prototype.DEFAULT_LOOP_FOLLOWUP_PROMPT
import org.coralprotocol.coralserver.agent.runtime.prototype.DEFAULT_LOOP_INITIAL_BASE_PROMPT
import org.coralprotocol.coralserver.agent.runtime.prototype.DEFAULT_SYSTEM_PROMPT
import org.coralprotocol.coralserver.mcp.McpResourceName
import org.koin.core.component.KoinComponent
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.reflect.full.findAnnotation
import kotlin.text.Charsets

/*
    NOTE: This list is used in tests, resources/constants/coral-agent.toml must be updated to include any new constants
    that are added here.
 */
val stringReferenceConstants = buildMap {
    put("PROTOTYPE_DEFAULT_SYSTEM_PROMPT", DEFAULT_SYSTEM_PROMPT)
    put("PROTOTYPE_DEFAULT_LOOP_INITIAL_BASE_PROMPT", DEFAULT_LOOP_INITIAL_BASE_PROMPT)
    put("PROTOTYPE_DEFAULT_LOOP_FOLLOWUP_PROMPT", DEFAULT_LOOP_FOLLOWUP_PROMPT)
    put("CORAL_STATE_RESOURCE_URI", McpResourceName.STATE_RESOURCE_URI.toString())
    put("CORAL_INSTRUCTION_RESOURCE_URI", McpResourceName.INSTRUCTION_RESOURCE_URI.toString())
}

@Serializable
data class EncodingOptions(
    val base64: Boolean = false,
    val charset: String = Charsets.UTF_8.name(),
)

@Serializable
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface PotentialStringReference {
    val input: EncodingOptions?
    val output: EncodingOptions?

    @Serializable
    @SerialName("string")
    data class String(
        val value: kotlin.String,
        override val input: EncodingOptions? = null,
        override val output: EncodingOptions? = null,
    ) : PotentialStringReference

    @Serializable
    @SerialName("file")
    data class File(
        val path: kotlin.String,
        override val input: EncodingOptions? = null,
        override val output: EncodingOptions? = null
    ) : PotentialStringReference

    @Serializable
    @SerialName("url")
    data class Url(
        val url: kotlin.String,
        override val input: EncodingOptions? = null,
        override val output: EncodingOptions? = null,
    ) : PotentialStringReference

    @Serializable
    @SerialName("constant")
    data class Constant(
        val name: kotlin.String,
        override val input: EncodingOptions? = null,
        override val output: EncodingOptions? = null,
    ) : PotentialStringReference
}

open class RegistryAgentStringSerializer : KSerializer<String>, KoinComponent {
    open fun defaultInputEncodingOptions(reference: PotentialStringReference) =
        EncodingOptions()

    open fun defaultOutputEncodingOptions(reference: PotentialStringReference) =
        EncodingOptions()

    private val stringSerializer = PotentialStringReference.String.serializer()
    private val fileSerializer = PotentialStringReference.File.serializer()
    private val urlSerializer = PotentialStringReference.Url.serializer()
    private val constantSerializer = PotentialStringReference.Constant.serializer()

    private val potentialStringSerializerDiscriminator = run {
        val tomlDiscriminator = PotentialStringReference::class
            .findAnnotation<TomlClassDiscriminator>()?.discriminator
            ?: "type"

        val jsonDiscriminator = PotentialStringReference::class
            .findAnnotation<JsonClassDiscriminator>()?.discriminator
            ?: "type"

        require(tomlDiscriminator == jsonDiscriminator)
        tomlDiscriminator
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("String", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val reference = when (decoder) {
            is TomlDecoder -> {
                when (val element = decoder.decodeTomlElement()) {
                    is TomlLiteral if element.type == TomlLiteral.Type.String -> {
                        PotentialStringReference.String(element.content)
                    }

                    is TomlTable -> {
                        val type = element[potentialStringSerializerDiscriminator]?.asTomlLiteral()?.content
                            ?: throw SerializationException("Missing discriminator \"$potentialStringSerializerDiscriminator\" in string reference")

                        val element = TomlTable(element.filterKeys { it != potentialStringSerializerDiscriminator })
                        when (type) {
                            stringSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                                stringSerializer,
                                element
                            )

                            fileSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                                fileSerializer,
                                element
                            )

                            urlSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                                urlSerializer,
                                element
                            )

                            constantSerializer.descriptor.serialName -> decoder.toml.decodeFromTomlElement(
                                constantSerializer,
                                element
                            )

                            else -> {
                                throw SerializationException("Unknown string reference type: $type")
                            }
                        }

                    }

                    else -> {
                        throw SerializationException("Unsupported string type: ${element::class.simpleName}")
                    }
                }
            }

            is JsonDecoder -> {
                when (val element = decoder.decodeJsonElement()) {
                    is JsonPrimitive if element.isString -> {
                        PotentialStringReference.String(element.content)
                    }

                    is JsonObject -> {
                        val type = element[potentialStringSerializerDiscriminator]?.jsonPrimitive?.content
                            ?: throw SerializationException("Missing discriminator \"$potentialStringSerializerDiscriminator\" in string reference")

                        val element = JsonObject(element.filterKeys { it != potentialStringSerializerDiscriminator })
                        when (type) {
                            stringSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                                stringSerializer,
                                element
                            )

                            fileSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                                fileSerializer,
                                element
                            )

                            urlSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                                urlSerializer,
                                element
                            )

                            constantSerializer.descriptor.serialName -> decoder.json.decodeFromJsonElement(
                                constantSerializer,
                                element
                            )

                            else -> {
                                throw SerializationException("Unknown string reference type: $type")
                            }
                        }
                    }

                    else -> {
                        throw SerializationException("Unsupported string type: ${element::class.simpleName}")
                    }
                }
            }

            else -> throw SerializationException("Unsupported decoder type: ${decoder::class.simpleName}")
        }

        val inputEncodingOptions = reference.input ?: defaultInputEncodingOptions(reference)
        val outputEncodingOptions = reference.output ?: defaultOutputEncodingOptions(reference)

        val inputCharset = Charsets.forName(inputEncodingOptions.charset)
        val outputCharset = Charsets.forName(outputEncodingOptions.charset)

        val bytes = when (reference) {
            is PotentialStringReference.File -> {
                val context = registryAgentSerializationContext.get()
                    ?: throw SerializationException("File references require a serialization context")

                if (!context.enableFileReferences)
                    throw SerializationException("File references are not enabled")

                val file = File(reference.path)
                if (file.isAbsolute || context.agentFilePath == null) {
                    file.readBytes()
                } else {
                    context.agentFilePath.toFile().resolve(file).readBytes()
                }
            }

            is PotentialStringReference.String -> reference.value.toByteArray(inputCharset)
            is PotentialStringReference.Url -> {
                val context = registryAgentSerializationContext.get()
                    ?: throw SerializationException("URL references require a serialization context")

                if (!context.enableUrlReferences)
                    throw SerializationException("Url references are not enabled")

                runBlocking {
                    context.httpClient.get(reference.url).bodyAsBytes()
                }
            }

            is PotentialStringReference.Constant -> {
                stringReferenceConstants[reference.name]?.toByteArray(inputCharset)
                    ?: throw SerializationException("Constant ${reference.name} not found")
            }
        }

        // If input and output settings are equal, no transformation is required
        if (inputEncodingOptions == outputEncodingOptions)
            return String(bytes, outputCharset)

        val inputBytes = if (inputEncodingOptions.base64) Base64.decode(bytes) else bytes
        val inputText = String(inputBytes, inputCharset)

        // The output character set is only important if outputting base64, otherwise the Java string type is used
        return if (outputEncodingOptions.base64) {
            Base64.encode(inputText.toByteArray(outputCharset))
        } else {
            inputText
        }
    }

}

/**
 * This class is used for blob options where default values for base64 encoding should be set
 */
class RegistryAgentBase64StringSerializer : RegistryAgentStringSerializer() {
    override fun defaultInputEncodingOptions(reference: PotentialStringReference): EncodingOptions {
        return when (reference) {
            is PotentialStringReference.Constant -> EncodingOptions()
            is PotentialStringReference.File -> EncodingOptions()

            // Inline strings should be written as base64 strings
            is PotentialStringReference.String -> EncodingOptions(base64 = true)
            is PotentialStringReference.Url -> EncodingOptions()
        }
    }

    override fun defaultOutputEncodingOptions(reference: PotentialStringReference): EncodingOptions {
        return EncodingOptions(base64 = true)
    }
}

object RegistryAgentStringListSerializer :
    KSerializer<List<String>> by ListSerializer(RegistryAgentStringSerializer())

object RegistryAgentBase64StringListSerializer :
    KSerializer<List<String>> by ListSerializer(RegistryAgentBase64StringSerializer())