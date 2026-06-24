package org.coralprotocol.coralserver.util

import dev.eav.tomlkt.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

sealed interface AbstractElement {
    /**
     * Often the custom deserialization of a polymorphic type involves reading a discriminator field, which must then
     * be removed from the element as it is not part of the type that it is deserializing into.  This is a helper
     * function to do that.
     */
    fun takeDiscriminator(name: String): String

    data class Toml(var tomlElement: TomlElement) : AbstractElement {
        override fun takeDiscriminator(name: String): String {
            val table = tomlElement.asTomlTable()
            val discriminator = table[name] ?: throw SerializationException("Missing discriminator $name")

            tomlElement = TomlTable(table.filterKeys { it != name })

            return discriminator.asTomlLiteral().content
        }
    }

    data class Json(var jsonElement: JsonElement) : AbstractElement {
        override fun takeDiscriminator(name: String): String {
            val discriminator = jsonElement.jsonObject[name]?.jsonPrimitive?.content
                ?: throw SerializationException("Missing discriminator $name")

            jsonElement = JsonObject(jsonElement.jsonObject.filterKeys { it != name })

            return discriminator
        }
    }
}

fun Decoder.decodeElement(): AbstractElement {
    return when (this) {
        is JsonDecoder -> AbstractElement.Json(decodeJsonElement())
        is TomlDecoder -> AbstractElement.Toml(decodeTomlElement())
        else -> throw SerializationException("Unsupported decoder type: ${this::class.simpleName}")
    }
}


fun <T> Decoder.decodeFromElement(deserializer: DeserializationStrategy<T>, element: AbstractElement): T {
    return when (element) {
        is AbstractElement.Json -> {
            if (this !is JsonDecoder)
                throw SerializationException("Expected a JsonDecoder")

            this.json.decodeFromJsonElement(deserializer, element.jsonElement)
        }

        is AbstractElement.Toml -> {
            if (this !is TomlDecoder)
                throw SerializationException("Expected a TomlDecoder")

            this.toml.decodeFromTomlElement(deserializer, element.tomlElement)
        }
    }
}

fun <T> Encoder.encodeDiscriminatedElement(
    serializer: SerializationStrategy<T>,
    value: T,
    discriminator: String,
) {
    when (this) {
        is JsonEncoder -> {
            val element = json.encodeToJsonElement(serializer, value)
            encodeJsonElement(
                JsonObject(
                    mapOf(
                        discriminator to JsonPrimitive(
                            serializer.descriptor.serialName
                        )
                    ) + element.jsonObject.toMap()
                )
            )
        }

        is TomlEncoder -> {
            val element = toml.encodeToTomlElement(serializer, value)
            encodeTomlElement(
                TomlTable(
                    mapOf(
                        discriminator to TomlLiteral(
                            serializer.descriptor.serialName
                        )
                    ) + element.asTomlTable().toMap()
                )
            )
        }

        else -> throw SerializationException("Unsupported decoder type: ${this::class.simpleName}")
    }
}