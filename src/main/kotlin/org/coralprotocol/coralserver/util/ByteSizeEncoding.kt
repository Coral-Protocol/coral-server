@file:OptIn(InternalSerializationApi::class)

package org.coralprotocol.coralserver.util

import com.sksamuel.hoplite.*
import com.sksamuel.hoplite.decoder.Decoder
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import dev.eav.tomlkt.TomlDecoder
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import me.saket.bytesize.*
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * [stringToByteSize] uses [String.endsWith] to match the unit, so the order of the sizes is important.  If `B` comes
 * first it will match `1024KB` and try to parse `1024K` as a number
 */
val byteSizeConverters = listOf<Pair<String, (Number) -> ByteSize>>(
    "KiB" to { it.kibibytes },
    "MiB" to { it.mebibytes },
    "GiB" to { it.gibibytes },
    "KB" to { it.kilobytes },
    "MB" to { it.megabytes },
    "GB" to { it.gigabytes },
    "B" to { it.binaryBytes },
)

fun stringToByteSize(string: String): ByteSize {
    val units = byteSizeConverters.joinToString(", ") { it.first }

    val (size, fn) = byteSizeConverters.firstOrNull { string.endsWith(it.first, ignoreCase = true) }
        ?: throw IllegalArgumentException("Invalid format \"${string}\", expected \"<size> <unit>\".  Valid units: $units")

    return fn(
        string.substring(0, string.length - size.length).toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid size value '${size}' in '${string}', expected a floating point number")
    )
}

class ByteSizeDecoder : Decoder<ByteSize> {
    override fun supports(type: KType): Boolean = type == typeOf<ByteSize>()

    override fun decode(node: Node, type: KType, context: DecoderContext): ConfigResult<ByteSize> {
        return when (node) {
            is StringNode -> parseAsString(node)
            is MapNode -> parseMap(node)
            else -> ConfigFailure.Generic(
                "Unexpected node ${node::class.simpleName}, expecting a string or a map"
            ).invalid()
        }
    }

    private fun parseMap(node: MapNode): ConfigResult<ByteSize> {
        val size = node["size"] as? NumberNode ?: return ConfigFailure.Generic("Missing size field").invalid()
        val unit = node["unit"] as? StringNode ?: return ConfigFailure.Generic("Missing unit field").invalid()
        return try {
            stringToByteSize("${size.value} ${unit.value}").valid()
        } catch (e: IllegalArgumentException) {
            ConfigFailure.Generic(e.message ?: "Unknown error").invalid()
        }
    }

    private fun parseAsString(node: StringNode): ConfigResult<ByteSize> {
        return try {
            stringToByteSize(node.value).valid()
        } catch (e: IllegalArgumentException) {
            ConfigFailure.Generic(e.message ?: "Unknown error").invalid()
        }
    }
}

class ByteSizeSerializer : KSerializer<ByteSize> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        "ByteSize",
        SerialKind.CONTEXTUAL
    )

    override fun serialize(encoder: Encoder, value: ByteSize) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): ByteSize {

        when (decoder) {
            is TomlDecoder -> {
                when (val element = decoder.decodeTomlElement()) {
                    is TomlTable -> {
                        val size = element["size"] as? TomlLiteral ?: throw SerializationException("Missing size field")
                        val unit = element["unit"] as? TomlLiteral ?: throw SerializationException("Missing unit field")
                        return stringToByteSize("$size $unit")
                    }

                    is TomlLiteral -> {
                        return stringToByteSize(element.toString())
                    }

                    else -> throw SerializationException("Expected a table or literal, got ${element::class.simpleName}")
                }
            }

            is JsonDecoder -> {
                when (val element = decoder.decodeJsonElement()) {
                    is JsonPrimitive if element.isString -> {
                        return stringToByteSize(element.jsonPrimitive.content)
                    }

                    is JsonObject -> {
                        val size =
                            element["size"] as? JsonPrimitive ?: throw SerializationException("Missing size field")

                        val unit =
                            element["unit"] as? JsonPrimitive ?: throw SerializationException("Missing unit field")

                        return stringToByteSize("${size.content} ${unit.content}")
                    }

                    else -> {
                        throw SerializationException("Expected a object or string, got ${element::class.simpleName}")
                    }
                }
            }

            else -> throw SerializationException("Unsupported decoder")
        }
    }
}