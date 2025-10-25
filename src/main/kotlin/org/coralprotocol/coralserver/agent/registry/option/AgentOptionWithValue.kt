package org.coralprotocol.coralserver.agent.registry.option

import io.ktor.util.*

sealed interface AgentOptionWithValue {
    data class String(
        val option: AgentOption.String,
        val value: kotlin.String
    ) : AgentOptionWithValue

    data class StringList(
        val option: AgentOption.StringList,
        val value: List<kotlin.String>
    ) : AgentOptionWithValue

    data class Blob(
        val option: AgentOption.Blob,
        val value: kotlin.String
    ) : AgentOptionWithValue

    data class BlobList(
        val option: AgentOption.BlobList,
        val value: List<kotlin.String>
    ) : AgentOptionWithValue

    data class Boolean(
        val option: AgentOption.Boolean,
        val value: kotlin.Boolean
    ) : AgentOptionWithValue

    data class Byte(
        val option: AgentOption.Byte,
        val value: kotlin.Byte
    ) : AgentOptionWithValue

    data class ByteList(
        val option: AgentOption.ByteList,
        val value: List<kotlin.Byte>
    ) : AgentOptionWithValue

    data class Short(
        val option: AgentOption.Short,
        val value: kotlin.Short
    ) : AgentOptionWithValue

    data class ShortList(
        val option: AgentOption.ShortList,
        val value: List<kotlin.Short>
    ) : AgentOptionWithValue

    data class Int(
        val option: AgentOption.Int,
        val value: kotlin.Int
    ) : AgentOptionWithValue

    data class IntList(
        val option: AgentOption.IntList,
        val value: List<kotlin.Int>
    ) : AgentOptionWithValue

    data class Long(
        val option: AgentOption.Long,
        val value: kotlin.Long
    ) : AgentOptionWithValue

    data class LongList(
        val option: AgentOption.LongList,
        val value: List<kotlin.Long>
    ) : AgentOptionWithValue

    data class UByte(
        val option: AgentOption.UByte,
        val value: kotlin.UByte
    ) : AgentOptionWithValue

    data class UByteList(
        val option: AgentOption.UByteList,
        val value: List<kotlin.UByte>
    ) : AgentOptionWithValue

    data class UShort(
        val option: AgentOption.UShort,
        val value: kotlin.UShort
    ) : AgentOptionWithValue

    data class UShortList(
        val option: AgentOption.UShortList,
        val value: List<kotlin.UShort>
    ) : AgentOptionWithValue

    data class UInt(
        val option: AgentOption.UInt,
        val value: kotlin.UInt
    ) : AgentOptionWithValue

    data class UIntList(
        val option: AgentOption.UIntList,
        val value: List<kotlin.UInt>
    ) : AgentOptionWithValue

    data class ULong(
        val option: AgentOption.ULong,
        val value: kotlin.ULong
    ) : AgentOptionWithValue

    data class ULongList(
        val option: AgentOption.ULongList,
        val value: List<kotlin.ULong>
    ) : AgentOptionWithValue

    data class Float(
        val option: AgentOption.Float,
        val value: kotlin.Float
    ) : AgentOptionWithValue

    data class FloatList(
        val option: AgentOption.FloatList,
        val value: List<kotlin.Float>
    ) : AgentOptionWithValue

    data class Double(
        val option: AgentOption.Double,
        val value: kotlin.Double
    ) : AgentOptionWithValue

    data class DoubleList(
        val option: AgentOption.DoubleList,
        val value: List<kotlin.Double>
    ) : AgentOptionWithValue
}

/**
 * Converts the option's value back into a wrapped type for on-wire use.
 */
fun AgentOptionWithValue.toWrappedValue(): AgentOptionValue = when (this) {
    is AgentOptionWithValue.Blob -> AgentOptionValue.Blob(value)
    is AgentOptionWithValue.BlobList -> AgentOptionValue.BlobList(value)
    is AgentOptionWithValue.Boolean -> AgentOptionValue.Boolean(value)
    is AgentOptionWithValue.Byte -> AgentOptionValue.Byte(value)
    is AgentOptionWithValue.ByteList -> AgentOptionValue.ByteList(value)
    is AgentOptionWithValue.Double -> AgentOptionValue.Double(value)
    is AgentOptionWithValue.DoubleList -> AgentOptionValue.DoubleList(value)
    is AgentOptionWithValue.Float -> AgentOptionValue.Float(value)
    is AgentOptionWithValue.FloatList -> AgentOptionValue.FloatList(value)
    is AgentOptionWithValue.Int -> AgentOptionValue.Int(value)
    is AgentOptionWithValue.IntList -> AgentOptionValue.IntList(value)
    is AgentOptionWithValue.Long -> AgentOptionValue.Long(value)
    is AgentOptionWithValue.LongList -> AgentOptionValue.LongList(value)
    is AgentOptionWithValue.Short -> AgentOptionValue.Short(value)
    is AgentOptionWithValue.ShortList -> AgentOptionValue.ShortList(value)
    is AgentOptionWithValue.String -> AgentOptionValue.String(value)
    is AgentOptionWithValue.StringList -> AgentOptionValue.StringList(value)
    is AgentOptionWithValue.UByte -> AgentOptionValue.UByte(value)
    is AgentOptionWithValue.UByteList -> AgentOptionValue.UByteList(value)
    is AgentOptionWithValue.UInt -> AgentOptionValue.UInt(value)
    is AgentOptionWithValue.UIntList -> AgentOptionValue.UIntList(value)
    is AgentOptionWithValue.ULong -> AgentOptionValue.ULong(value)
    is AgentOptionWithValue.ULongList -> AgentOptionValue.ULongList(value)
    is AgentOptionWithValue.UShort -> AgentOptionValue.UShort(value)
    is AgentOptionWithValue.UShortList -> AgentOptionValue.UShortList(value)
}

/**
 * Converts an AgentOptionWithValue to a string value that can be used to set an environment variable.
 * Use [AgentOptionWithValue.toDisplayString] if you intend to log the result; this function will censor secret data.
 */
fun AgentOptionWithValue.toStringValue(): String = when (this) {
    is AgentOptionWithValue.Blob -> value.encodeBase64()
    is AgentOptionWithValue.BlobList -> value.joinToString(",") { it.encodeBase64() }
    is AgentOptionWithValue.Boolean -> if (value) "1" else "0"
    is AgentOptionWithValue.Byte -> value.toString()
    is AgentOptionWithValue.ByteList -> value.joinToString(",")
    is AgentOptionWithValue.Double -> value.toString()
    is AgentOptionWithValue.DoubleList -> value.joinToString(",")
    is AgentOptionWithValue.Float -> value.toString()
    is AgentOptionWithValue.FloatList -> value.joinToString(",")
    is AgentOptionWithValue.Int -> value.toString()
    is AgentOptionWithValue.IntList -> value.joinToString(",")
    is AgentOptionWithValue.Long -> value.toString()
    is AgentOptionWithValue.LongList -> value.joinToString(",")
    is AgentOptionWithValue.Short -> value.toString()
    is AgentOptionWithValue.ShortList -> value.joinToString(",")
    is AgentOptionWithValue.String -> if (option.base64) value.encodeBase64() else value
    is AgentOptionWithValue.StringList -> value.joinToString(",") {
        if (option.base64) it.encodeBase64() else it
    }
    is AgentOptionWithValue.UByte -> value.toString()
    is AgentOptionWithValue.UByteList -> value.joinToString(",")
    is AgentOptionWithValue.UInt -> value.toString()
    is AgentOptionWithValue.UIntList -> value.joinToString(",")
    is AgentOptionWithValue.ULong -> value.toString()
    is AgentOptionWithValue.ULongList -> value.joinToString(",")
    is AgentOptionWithValue.UShort -> value.toString()
    is AgentOptionWithValue.UShortList -> value.joinToString(",")
}

/**
 * Prints the value of this option with secrets censored and with no Base64.  This function should only be used for
 * logging, this will the incorrect value for environment variables.
 */
fun AgentOptionWithValue.toDisplayString(): String = when (this) {
    is AgentOptionWithValue.Blob -> value.encodeBase64()
    is AgentOptionWithValue.BlobList -> value.joinToString(",") { it.encodeBase64() }
    is AgentOptionWithValue.Boolean -> if (value) "1" else "0"
    is AgentOptionWithValue.Byte -> value.toString()
    is AgentOptionWithValue.ByteList -> value.joinToString(",")
    is AgentOptionWithValue.Double -> value.toString()
    is AgentOptionWithValue.DoubleList -> value.joinToString(",")
    is AgentOptionWithValue.Float -> value.toString()
    is AgentOptionWithValue.FloatList -> value.joinToString(",")
    is AgentOptionWithValue.Int -> value.toString()
    is AgentOptionWithValue.IntList -> value.joinToString(",")
    is AgentOptionWithValue.Long -> value.toString()
    is AgentOptionWithValue.LongList -> value.joinToString(",")
    is AgentOptionWithValue.Short -> value.toString()
    is AgentOptionWithValue.ShortList -> value.joinToString(",")
    is AgentOptionWithValue.String -> {
        if (option.secret) {
            "*".repeat(value.length)
        }
        else {
            value
        }
    }
    is AgentOptionWithValue.StringList -> value.joinToString(",") {
        if (option.secret) {
            "*".repeat(it.length)
        }
        else {
            it
        }
    }
    is AgentOptionWithValue.UByte -> value.toString()
    is AgentOptionWithValue.UByteList -> value.joinToString(",")
    is AgentOptionWithValue.UInt -> value.toString()
    is AgentOptionWithValue.UIntList -> value.joinToString(",")
    is AgentOptionWithValue.ULong -> value.toString()
    is AgentOptionWithValue.ULongList -> value.joinToString(",")
    is AgentOptionWithValue.UShort -> value.toString()
    is AgentOptionWithValue.UShortList -> value.joinToString(",")
}