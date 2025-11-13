@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.option

import io.ktor.util.encodeBase64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import java.nio.ByteBuffer

@Serializable
@JsonClassDiscriminator("type")
sealed interface AgentOptionValue {
    @Serializable
    @SerialName("string")
    data class String(val value: kotlin.String) : AgentOptionValue

    @Serializable
    @SerialName("list[string]")
    data class StringList(val value: List<kotlin.String>) : AgentOptionValue

    @Serializable
    @SerialName("blob")
    data class Blob(@Suppress("ArrayInDataClass") val value: ByteArray) : AgentOptionValue

    @Serializable
    @SerialName("list[blob]")
    data class BlobList(val value: List<ByteArray>) : AgentOptionValue

    @Serializable
    @SerialName("bool")
    data class Boolean(val value: kotlin.Boolean) : AgentOptionValue

    @Serializable
    @SerialName("i8")
    data class Byte(val value: kotlin.Byte) : AgentOptionValue

    @Serializable
    @SerialName("list[i8]")
    data class ByteList(val value: List<kotlin.Byte>) : AgentOptionValue

    @Serializable
    @SerialName("i16")
    data class Short(val value: kotlin.Short) : AgentOptionValue

    @Serializable
    @SerialName("list[i16]")
    data class ShortList(val value: List<kotlin.Short>) : AgentOptionValue

    @Serializable
    @SerialName("i32")
    data class Int(val value: kotlin.Int) : AgentOptionValue

    @Serializable
    @SerialName("list[i32]")
    data class IntList(val value: List<kotlin.Int>) : AgentOptionValue

    @Serializable
    @SerialName("i64")
    data class Long(val value: kotlin.Long) : AgentOptionValue

    @Serializable
    @SerialName("list[i64]")
    data class LongList(val value: List<kotlin.Long>) : AgentOptionValue

    @Serializable
    @SerialName("u8")
    data class UByte(val value: kotlin.UByte) : AgentOptionValue

    @Serializable
    @SerialName("list[u8]")
    data class UByteList(val value: List<kotlin.UByte>) : AgentOptionValue

    @Serializable
    @SerialName("u16")
    data class UShort(val value: kotlin.UShort) : AgentOptionValue

    @Serializable
    @SerialName("list[u16]")
    data class UShortList(val value: List<kotlin.UShort>) : AgentOptionValue

    @Serializable
    @SerialName("u32")
    data class UInt(val value: kotlin.UInt) : AgentOptionValue

    @Serializable
    @SerialName("list[u32]")
    data class UIntList(val value: List<kotlin.UInt>) : AgentOptionValue

    /**
     * OpenAPI does not support unsigned long
     */
    @Serializable
    @SerialName("u64")
    data class ULong(val value: kotlin.String) : AgentOptionValue

    /**
     * OpenAPI does not support unsigned long
     */
    @Serializable
    @SerialName("list[u64]")
    data class ULongList(val value: List<kotlin.String>) : AgentOptionValue

    @Serializable
    @SerialName("f32")
    data class Float(val value: kotlin.Float) : AgentOptionValue

    @Serializable
    @SerialName("list[f32]")
    data class FloatList(val value: List<kotlin.Float>) : AgentOptionValue

    @Serializable
    @SerialName("f64")
    data class Double(val value: kotlin.Double) : AgentOptionValue

    @Serializable
    @SerialName("list[f64]")
    data class DoubleList(val value: List<kotlin.Double>) : AgentOptionValue
}

/**
 * Returns a string representation of the [AgentOptionValue] suitable for use as an environment variable.
 *
 * Note that unlike [AgentOptionValue.toFileSystemValue] this function returns a single string that represents all
 * values.  Note also that a comma separates items in a list ",".  For [AgentOptionValue.StringList] make sure
 * `base64 = true` if it is at all possible a given value contains a comma.
 */
fun AgentOptionValue.asEnvVarValue(base64: Boolean = false): String = when (this) {
    is AgentOptionValue.Blob -> value.encodeBase64()
    is AgentOptionValue.BlobList -> value.joinToString(",") { it.encodeBase64() }
    is AgentOptionValue.Boolean -> if (value) "1" else "0"
    is AgentOptionValue.Byte -> value.toString()
    is AgentOptionValue.ByteList -> value.joinToString(",")
    is AgentOptionValue.Double -> value.toString()
    is AgentOptionValue.DoubleList -> value.joinToString(",")
    is AgentOptionValue.Float -> value.toString()
    is AgentOptionValue.FloatList -> value.joinToString(",")
    is AgentOptionValue.Int -> value.toString()
    is AgentOptionValue.IntList -> value.joinToString(",")
    is AgentOptionValue.Long -> value.toString()
    is AgentOptionValue.LongList -> value.joinToString(",")
    is AgentOptionValue.Short -> value.toString()
    is AgentOptionValue.ShortList -> value.joinToString(",")
    is AgentOptionValue.String -> if (base64) value.encodeBase64() else value
    is AgentOptionValue.StringList -> value.joinToString(",") {
        if (base64) it.encodeBase64() else it
    }
    is AgentOptionValue.UByte -> value.toString()
    is AgentOptionValue.UByteList -> value.joinToString(",")
    is AgentOptionValue.UInt -> value.toString()
    is AgentOptionValue.UIntList -> value.joinToString(",")
    is AgentOptionValue.ULong -> value.toString()
    is AgentOptionValue.ULongList -> value.joinToString(",")
    is AgentOptionValue.UShort -> value.toString()
    is AgentOptionValue.UShortList -> value.joinToString(",")
}

/**
 * Returns a list of byte arrays that represent the [AgentOptionValue] suitable for use written to a file on the
 * filesystem.  Because value lists are likely to be written to separate files, this function will return a list in all
 * cases.  When the wrapped type is not a list, a list with one value will be returned.
 *
 * Encoding notes:
 * - [AgentOptionValue.Blob] and [AgentOptionValue.BlobList] will write their bytes directly to file.
 * - [AgentOptionValue.String] and [AgentOptionValue.StringList] will be in UTF-8.
 * - [AgentOptionValue.Boolean] will be written as a single byte of the value '1' for true and '0' for false.
 * - Numeric types are written in binary, in big-endian order.
 */
fun AgentOptionValue.toFileSystemValue(): List<ByteArray> = when (this) {
    is AgentOptionValue.Blob -> listOf(value)
    is AgentOptionValue.BlobList -> value
    is AgentOptionValue.Boolean -> listOf(ByteBuffer.allocate(Byte.SIZE_BYTES).put(if (value) 1 else 0).array())
    is AgentOptionValue.Byte -> listOf(ByteBuffer.allocate(Byte.SIZE_BYTES).put(value).array())
    is AgentOptionValue.ByteList -> value.map { ByteBuffer.allocate(Byte.SIZE_BYTES).put(it).array() }
    is AgentOptionValue.Double -> listOf(ByteBuffer.allocate(Double.SIZE_BYTES).putDouble(value).array())
    is AgentOptionValue.DoubleList -> value.map { ByteBuffer.allocate(Double.SIZE_BYTES).putDouble(it).array() }
    is AgentOptionValue.Float -> listOf(ByteBuffer.allocate(Float.SIZE_BYTES).putFloat(value).array())
    is AgentOptionValue.FloatList -> value.map { ByteBuffer.allocate(Float.SIZE_BYTES).putFloat(it).array() }
    is AgentOptionValue.Int -> listOf(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())
    is AgentOptionValue.IntList -> value.map { ByteBuffer.allocate(Int.SIZE_BYTES).putInt(it).array() }
    is AgentOptionValue.Long -> listOf(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array())
    is AgentOptionValue.LongList -> value.map { ByteBuffer.allocate(Long.SIZE_BYTES).putLong(it).array() }
    is AgentOptionValue.Short -> listOf(ByteBuffer.allocate(Short.SIZE_BYTES).putShort(value).array())
    is AgentOptionValue.ShortList -> value.map { ByteBuffer.allocate(Short.SIZE_BYTES).putShort(it).array() }
    is AgentOptionValue.String -> listOf(value.encodeToByteArray())
    is AgentOptionValue.StringList -> value.map { it.encodeToByteArray() }
    is AgentOptionValue.UByte -> listOf(ByteBuffer.allocate(UByte.SIZE_BYTES).put(value.toByte()).array())
    is AgentOptionValue.UByteList -> value.map { ByteBuffer.allocate(UByte.SIZE_BYTES).put(it.toByte()).array() }
    is AgentOptionValue.UInt -> listOf(ByteBuffer.allocate(UInt.SIZE_BYTES).putInt(value.toInt()).array())
    is AgentOptionValue.UIntList -> value.map { ByteBuffer.allocate(UInt.SIZE_BYTES).putInt(it.toInt()).array() }
    is AgentOptionValue.ULong -> listOf(ByteBuffer.allocate(ULong.SIZE_BYTES).putLong(value.toULong().toLong()).array())
    is AgentOptionValue.ULongList -> value.map { ByteBuffer.allocate(ULong.SIZE_BYTES).putLong(it.toULong().toLong()).array() }
    is AgentOptionValue.UShort -> listOf(ByteBuffer.allocate(UShort.SIZE_BYTES).putShort(value.toShort()).array())
    is AgentOptionValue.UShortList -> value.map { ByteBuffer.allocate(UShort.SIZE_BYTES).putShort(it.toShort()).array() }
}