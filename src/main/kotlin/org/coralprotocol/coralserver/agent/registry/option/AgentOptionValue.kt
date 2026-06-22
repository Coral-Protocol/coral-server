@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.option

import dev.eav.tomlkt.TomlClassDiscriminator
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.util.decodeElement
import org.coralprotocol.coralserver.util.decodeFromElement
import org.coralprotocol.coralserver.util.encodeDiscriminatedElement
import java.nio.ByteBuffer
import kotlin.io.encoding.Base64

interface AgentOptionListValue<T> {
    val value: List<T>
}

typealias AgentOptionValue = PolymorphicAgentOptionValue<*>

@Serializable(with = AgentOptionValueSerializer::class)
sealed interface PolymorphicAgentOptionValue<BackingType> {
    val value: BackingType
    fun toFileSystemValue(): List<ByteArray>

    @Serializable
    @SerialName(TYPE_STRING)
    data class String(override val value: kotlin.String) : PolymorphicAgentOptionValue<kotlin.String> {
        override fun toFileSystemValue() = listOf(value.encodeToByteArray())
    }

    @Serializable
    @SerialName(TYPE_STRING_LIST)
    data class StringList(override val value: List<kotlin.String>) : PolymorphicAgentOptionValue<List<kotlin.String>>,
        AgentOptionListValue<kotlin.String> {
        override fun toFileSystemValue() = value.map { it.encodeToByteArray() }
    }

    @Serializable
    @SerialName(TYPE_BLOB)
    data class Blob(override val value: kotlin.String) : PolymorphicAgentOptionValue<kotlin.String> {
        companion object {
            fun fromBytes(bytes: ByteArray) = Blob(Base64.encode(bytes))
        }

        @Transient
        val bytes = Base64.decode(value)

        override fun toFileSystemValue() = listOf(bytes)
    }

    @Serializable
    @SerialName(TYPE_BLOB_LIST)
    data class BlobList(override val value: List<kotlin.String>) : PolymorphicAgentOptionValue<List<kotlin.String>>,
        AgentOptionListValue<kotlin.String> {
        companion object {
            fun fromByteList(byteList: List<ByteArray>) = BlobList(byteList.map { Base64.encode(it) })
        }

        @Transient
        val bytes = value.map { Base64.decode(it) }
        override fun toFileSystemValue() = bytes
    }

    @Serializable
    @SerialName(TYPE_BOOLEAN)
    data class Boolean(override val value: kotlin.Boolean) : PolymorphicAgentOptionValue<kotlin.Boolean> {
        override fun toFileSystemValue() =
            listOf(ByteBuffer.allocate(kotlin.Byte.SIZE_BYTES).put(if (value) 1 else 0).array())
    }

    @Serializable
    @SerialName(TYPE_BYTE)
    data class Byte(override val value: kotlin.Byte) : PolymorphicAgentOptionValue<kotlin.Byte> {
        override fun toFileSystemValue() = listOf(ByteBuffer.allocate(kotlin.Byte.SIZE_BYTES).put(value).array())
    }

    @Serializable
    @SerialName(TYPE_BYTE_LIST)
    data class ByteList(override val value: List<kotlin.Byte>) : PolymorphicAgentOptionValue<List<kotlin.Byte>>,
        AgentOptionListValue<kotlin.Byte> {
        override fun toFileSystemValue() = value.map { ByteBuffer.allocate(kotlin.Byte.SIZE_BYTES).put(it).array() }
    }

    @Serializable
    @SerialName(TYPE_SHORT)
    data class Short(override val value: kotlin.Short) : PolymorphicAgentOptionValue<kotlin.Short> {
        override fun toFileSystemValue() = listOf(ByteBuffer.allocate(kotlin.Short.SIZE_BYTES).putShort(value).array())
    }

    @Serializable
    @SerialName(TYPE_SHORT_LIST)
    data class ShortList(override val value: List<kotlin.Short>) : PolymorphicAgentOptionValue<List<kotlin.Short>>,
        AgentOptionListValue<kotlin.Short> {
        override fun toFileSystemValue() =
            value.map { ByteBuffer.allocate(kotlin.Short.SIZE_BYTES).putShort(it).array() }
    }

    @Serializable
    @SerialName(TYPE_INT)
    data class Int(override val value: kotlin.Int) : PolymorphicAgentOptionValue<kotlin.Int> {
        override fun toFileSystemValue() = listOf(ByteBuffer.allocate(kotlin.Int.SIZE_BYTES).putInt(value).array())
    }

    @Serializable
    @SerialName(TYPE_INT_LIST)
    data class IntList(override val value: List<kotlin.Int>) : PolymorphicAgentOptionValue<List<kotlin.Int>>,
        AgentOptionListValue<kotlin.Int> {
        override fun toFileSystemValue() = value.map { ByteBuffer.allocate(kotlin.Int.SIZE_BYTES).putInt(it).array() }
    }

    @Serializable
    @SerialName(TYPE_LONG)
    data class Long(override val value: kotlin.Long) : PolymorphicAgentOptionValue<kotlin.Long> {
        override fun toFileSystemValue() = listOf(ByteBuffer.allocate(kotlin.Long.SIZE_BYTES).putLong(value).array())
    }

    @Serializable
    @SerialName(TYPE_LONG_LIST)
    data class LongList(override val value: List<kotlin.Long>) : PolymorphicAgentOptionValue<List<kotlin.Long>>,
        AgentOptionListValue<kotlin.Long> {
        override fun toFileSystemValue() = value.map { ByteBuffer.allocate(kotlin.Long.SIZE_BYTES).putLong(it).array() }
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_BYTE)
    data class UByte(override val value: kotlin.UByte) : PolymorphicAgentOptionValue<kotlin.UByte> {
        override fun toFileSystemValue() =
            listOf(ByteBuffer.allocate(kotlin.UByte.SIZE_BYTES).put(value.toByte()).array())
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_BYTE_LIST)
    data class UByteList(override val value: List<kotlin.UByte>) : PolymorphicAgentOptionValue<List<kotlin.UByte>>,
        AgentOptionListValue<kotlin.UByte> {
        override fun toFileSystemValue() =
            value.map { ByteBuffer.allocate(kotlin.UByte.SIZE_BYTES).put(it.toByte()).array() }
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_SHORT)
    data class UShort(override val value: kotlin.UShort) : PolymorphicAgentOptionValue<kotlin.UShort> {
        override fun toFileSystemValue() =
            listOf(ByteBuffer.allocate(kotlin.UShort.SIZE_BYTES).putShort(value.toShort()).array())
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_SHORT_LIST)
    data class UShortList(override val value: List<kotlin.UShort>) : PolymorphicAgentOptionValue<List<kotlin.UShort>>,
        AgentOptionListValue<kotlin.UShort> {
        override fun toFileSystemValue() =
            value.map { ByteBuffer.allocate(kotlin.UShort.SIZE_BYTES).putShort(it.toShort()).array() }
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_INT)
    data class UInt(override val value: kotlin.UInt) : PolymorphicAgentOptionValue<kotlin.UInt> {
        override fun toFileSystemValue() =
            listOf(ByteBuffer.allocate(kotlin.UInt.SIZE_BYTES).putInt(value.toInt()).array())
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_INT_LIST)
    data class UIntList(override val value: List<kotlin.UInt>) : PolymorphicAgentOptionValue<List<kotlin.UInt>>,
        AgentOptionListValue<kotlin.UInt> {
        override fun toFileSystemValue() =
            value.map { ByteBuffer.allocate(kotlin.UInt.SIZE_BYTES).putInt(it.toInt()).array() }
    }

    /**
     * OpenAPI does not support unsigned long
     */
    @Serializable
    @SerialName(TYPE_UNSIGNED_LONG)
    data class ULong(override val value: kotlin.String) : PolymorphicAgentOptionValue<kotlin.String> {
        override fun toFileSystemValue() =
            listOf(ByteBuffer.allocate(kotlin.ULong.SIZE_BYTES).putLong(value.toULong().toLong()).array())
    }

    /**
     * OpenAPI does not support unsigned long
     */
    @Serializable
    @SerialName(TYPE_UNSIGNED_LONG_LIST)
    data class ULongList(override val value: List<kotlin.String>) : PolymorphicAgentOptionValue<List<kotlin.String>>,
        AgentOptionListValue<kotlin.String> {
        override fun toFileSystemValue() =
            value.map { ByteBuffer.allocate(kotlin.ULong.SIZE_BYTES).putLong(it.toULong().toLong()).array() }
    }

    @Serializable
    @SerialName(TYPE_FLOAT)
    data class Float(override val value: kotlin.Float) : PolymorphicAgentOptionValue<kotlin.Float> {
        override fun toFileSystemValue() = listOf(ByteBuffer.allocate(kotlin.Float.SIZE_BYTES).putFloat(value).array())
    }

    @Serializable
    @SerialName(TYPE_FLOAT_LIST)
    data class FloatList(override val value: List<kotlin.Float>) : PolymorphicAgentOptionValue<List<kotlin.Float>>,
        AgentOptionListValue<kotlin.Float> {
        override fun toFileSystemValue() =
            value.map { ByteBuffer.allocate(kotlin.Float.SIZE_BYTES).putFloat(it).array() }
    }

    @Serializable
    @SerialName(TYPE_DOUBLE)
    data class Double(override val value: kotlin.Double) : PolymorphicAgentOptionValue<kotlin.Double> {
        override fun toFileSystemValue() =
            listOf(ByteBuffer.allocate(kotlin.Double.SIZE_BYTES).putDouble(value).array())
    }

    @Serializable
    @SerialName(TYPE_DOUBLE_LIST)
    data class DoubleList(override val value: List<kotlin.Double>) : PolymorphicAgentOptionValue<List<kotlin.Double>>,
        AgentOptionListValue<kotlin.Double> {
        override fun toFileSystemValue() =
            value.map { ByteBuffer.allocate(kotlin.Double.SIZE_BYTES).putDouble(it).array() }
    }
}

/**
 * Returns a string representation of the [PolymorphicAgentOptionValue] suitable for use as an environment variable.
 *
 * Note that unlike [PolymorphicAgentOptionValue.toFileSystemValue] this function returns a single string that represents all
 * values.  Note also that a comma separates items in a list ",".  For [PolymorphicAgentOptionValue.StringList] make sure
 * `base64 = true` if it is at all possible a given value contains a comma.
 */
fun <T> PolymorphicAgentOptionValue<T>.asEnvVarValue(base64: Boolean = false): String = when (this) {
    is PolymorphicAgentOptionValue.StringList -> value.joinToString(",") {
        if (base64) Base64.encode(it.encodeToByteArray()) else it
    }

    is AgentOptionListValue<*> -> value.joinToString(",")
    is PolymorphicAgentOptionValue.Boolean -> if (value) "1" else "0"
    is PolymorphicAgentOptionValue.String -> if (base64) Base64.encode(value.encodeToByteArray()) else value
    else -> value.toString()
}

@OptIn(InternalSerializationApi::class)
private val agentOptionValueSerializerMap: Map<String, KSerializer<out PolymorphicAgentOptionValue<*>>> =
    PolymorphicAgentOptionValue::class.sealedSubclasses.associate { kClass ->
        val serializer = kClass.serializer() as KSerializer<out PolymorphicAgentOptionValue<*>>
        serializer.descriptor.serialName to serializer
    }

/**
 * Kotlinx won't serialize sealed classes that are generic with star projection.  [PolymorphicAgentOptionValue] is a
 * sealed generic class but the type parameter does not affect serialization.  This custom serializer pretty much does
 * what the generated one would have without throwing errors for star projection.
 */
@OptIn(InternalSerializationApi::class)
class AgentOptionValueSerializer : KSerializer<AgentOptionValue> {
    private val discriminatorName = "type"

    // Same descriptor generated by SealedClassSerializer
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        "AgentOptionValue",
        PolymorphicKind.SEALED
    ) {
        annotations = listOf(JsonClassDiscriminator(discriminatorName), TomlClassDiscriminator(discriminatorName))

        element(discriminatorName, String.serializer().descriptor)
        element(
            "value",
            buildSerialDescriptor("kotlinx.serialization.Sealed<AgentOptionValue>", SerialKind.CONTEXTUAL) {
                agentOptionValueSerializerMap.forEach { (name, serializer) ->
                    element(name, serializer.descriptor)
                }
            })
    }

    override fun serialize(
        encoder: Encoder,
        value: AgentOptionValue
    ) {
        @Suppress("UNCHECKED_CAST")
        val serializer = value::class.serializer() as KSerializer<Any>
        encoder.encodeDiscriminatedElement(serializer, value, discriminatorName)
    }

    override fun deserialize(decoder: Decoder): AgentOptionValue {
        val element = decoder.decodeElement()
        val type = element.takeDiscriminator(discriminatorName)
        val serializer = agentOptionValueSerializerMap[type] ?: throw SerializationException("Unsupported type: $type")

        return decoder.decodeFromElement(serializer, element)
    }
}