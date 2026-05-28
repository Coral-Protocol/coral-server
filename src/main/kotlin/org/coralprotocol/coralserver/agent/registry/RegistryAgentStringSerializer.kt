@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import dev.eav.tomlkt.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
import java.nio.charset.Charset
import kotlin.io.encoding.Base64
import kotlin.reflect.full.findAnnotation

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
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface PotentialStringReference {
    val base64: Boolean?

    @Serializable
    @SerialName("string")
    data class String(
        val value: kotlin.String,
        override val base64: Boolean? = null
    ) : PotentialStringReference

    @Serializable
    @SerialName("file")
    data class File(
        val path: kotlin.String,
        val encoding: kotlin.String = "UTF-8",
        override val base64: Boolean? = null
    ) : PotentialStringReference

    @Serializable
    @SerialName("url")
    data class Url(
        val url: kotlin.String,
        val encoding: kotlin.String = "UTF-8",
        override val base64: Boolean? = null
    ) : PotentialStringReference

    @Serializable
    @SerialName("constant")
    data class Constant(
        val name: kotlin.String,
        override val base64: Boolean? = null
    ) : PotentialStringReference
}

open class RegistryAgentStringSerializer : KSerializer<String>, KoinComponent {
    open val base64Default: Boolean = false

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

        val text = when (reference) {
            is PotentialStringReference.File -> {
                val context = registryAgentSerializationContext.get()
                    ?: throw SerializationException("File references require a serialization context")

                if (!context.enableFileReferences)
                    throw SerializationException("File references are not enabled")

                val file = File(reference.path)
                if (file.isAbsolute || context.agentFilePath == null) {
                    file.readText(Charset.forName(reference.encoding))
                } else {
                    context.agentFilePath.toFile().resolve(file).readText(Charset.forName(reference.encoding))
                }
            }

            is PotentialStringReference.String -> reference.value
            is PotentialStringReference.Url -> {
                val context = registryAgentSerializationContext.get()
                    ?: throw SerializationException("URL references require a serialization context")

                if (!context.enableUrlReferences)
                    throw SerializationException("Url references are not enabled")

                runBlocking {
                    context.httpClient.get(reference.url).bodyAsText(Charset.forName(reference.encoding))
                }
            }

            is PotentialStringReference.Constant -> {
                stringReferenceConstants[reference.name]
                    ?: throw SerializationException("Constant ${reference.name} not found")
            }
        }

        val base64 = reference.base64 ?: base64Default
        return if (base64) {
            Base64.encode(text.encodeToByteArray())
        } else {
            text
        }
    }

}

class RegistryAgentBase64StringSerializer : RegistryAgentStringSerializer() {
    override val base64Default: Boolean
        get() = true
}

object RegistryAgentStringListSerializer :
    KSerializer<List<String>> by ListSerializer(RegistryAgentStringSerializer())

object RegistryAgentBase64StringListSerializer :
    KSerializer<List<String>> by ListSerializer(RegistryAgentBase64StringSerializer())