@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.option

import io.ktor.util.encodeBase64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface AgentOptionValue {
    @Serializable
    @SerialName("string")
    data class String(val value: kotlin.String) : AgentOptionValue

    @Serializable
    @SerialName("list[string]")
    data class StringList(val value: List<kotlin.String>) : AgentOptionValue

    // todo: maybe byte array
    @Serializable
    @SerialName("blob")
    data class Blob(val value: kotlin.String) : AgentOptionValue

    // todo: maybe byte array
    @Serializable
    @SerialName("list[blob]")
    data class BlobList(val value: List<kotlin.String>) : AgentOptionValue

    @Serializable
    @SerialName("bool")
    data class Boolean(val value: kotlin.Boolean) : AgentOptionValue

    @Serializable
    @SerialName("i8")
    data class Byte(val value: kotlin.Byte) : AgentOptionValue

    @Serializable
    @SerialName("list[byte]")
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

    @Serializable
    @SerialName("u64")
    data class ULong(val value: kotlin.ULong) : AgentOptionValue

    @Serializable
    @SerialName("list[u64]")
    data class ULongList(val value: List<kotlin.ULong>) : AgentOptionValue

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
 * Converts an [AgentOptionValue] to a string representation.  If [base64] is true, [AgentOptionValue.String] and
 * [AgentOptionValue.StringList] will be base64 encoded.
 */
fun AgentOptionValue.toStringValue(base64: Boolean = false): String = when (this) {
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