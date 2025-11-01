package org.coralprotocol.coralserver.agent.registry.option

import com.github.dockerjava.api.model.Volume
import io.ktor.util.*
import org.coralprotocol.coralserver.agent.exceptions.AgentOptionValidationException
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.writeBytes

sealed interface AgentOptionWithValue {
    data class String(
        val option: AgentOption.String,
        val value: AgentOptionValue.String
    ) : AgentOptionWithValue

    data class StringList(
        val option: AgentOption.StringList,
        val value: AgentOptionValue.StringList
    ) : AgentOptionWithValue

    data class Blob(
        val option: AgentOption.Blob,
        val value: AgentOptionValue.Blob
    ) : AgentOptionWithValue

    data class BlobList(
        val option: AgentOption.BlobList,
        val value: AgentOptionValue.BlobList
    ) : AgentOptionWithValue

    data class Boolean(
        val option: AgentOption.Boolean,
        val value: AgentOptionValue.Boolean
    ) : AgentOptionWithValue

    data class Byte(
        val option: AgentOption.Byte,
        val value: AgentOptionValue.Byte
    ) : AgentOptionWithValue

    data class ByteList(
        val option: AgentOption.ByteList,
        val value: AgentOptionValue.ByteList
    ) : AgentOptionWithValue

    data class Short(
        val option: AgentOption.Short,
        val value: AgentOptionValue.Short
    ) : AgentOptionWithValue

    data class ShortList(
        val option: AgentOption.ShortList,
        val value: AgentOptionValue.ShortList
    ) : AgentOptionWithValue

    data class Int(
        val option: AgentOption.Int,
        val value: AgentOptionValue.Int
    ) : AgentOptionWithValue

    data class IntList(
        val option: AgentOption.IntList,
        val value: AgentOptionValue.IntList
    ) : AgentOptionWithValue

    data class Long(
        val option: AgentOption.Long,
        val value: AgentOptionValue.Long
    ) : AgentOptionWithValue

    data class LongList(
        val option: AgentOption.LongList,
        val value: AgentOptionValue.LongList
    ) : AgentOptionWithValue

    data class UByte(
        val option: AgentOption.UByte,
        val value: AgentOptionValue.UByte
    ) : AgentOptionWithValue

    data class UByteList(
        val option: AgentOption.UByteList,
        val value: AgentOptionValue.UByteList
    ) : AgentOptionWithValue

    data class UShort(
        val option: AgentOption.UShort,
        val value: AgentOptionValue.UShort
    ) : AgentOptionWithValue

    data class UShortList(
        val option: AgentOption.UShortList,
        val value: AgentOptionValue.UShortList
    ) : AgentOptionWithValue

    data class UInt(
        val option: AgentOption.UInt,
        val value: AgentOptionValue.UInt
    ) : AgentOptionWithValue

    data class UIntList(
        val option: AgentOption.UIntList,
        val value: AgentOptionValue.UIntList
    ) : AgentOptionWithValue

    data class ULong(
        val option: AgentOption.ULong,
        val value: AgentOptionValue.ULong
    ) : AgentOptionWithValue

    data class ULongList(
        val option: AgentOption.ULongList,
        val value: AgentOptionValue.ULongList
    ) : AgentOptionWithValue

    data class Float(
        val option: AgentOption.Float,
        val value: AgentOptionValue.Float
    ) : AgentOptionWithValue

    data class FloatList(
        val option: AgentOption.FloatList,
        val value: AgentOptionValue.FloatList
    ) : AgentOptionWithValue

    data class Double(
        val option: AgentOption.Double,
        val value: AgentOptionValue.Double
    ) : AgentOptionWithValue

    data class DoubleList(
        val option: AgentOption.DoubleList,
        val value: AgentOptionValue.DoubleList
    ) : AgentOptionWithValue
}

/**
 * Extract the underlying value from an [AgentOptionWithValue] as a base [AgentOptionValue].
 */
@Suppress("DuplicatedCode")
fun AgentOptionWithValue.value(): AgentOptionValue = when (this) {
    is AgentOptionWithValue.Blob -> value
    is AgentOptionWithValue.BlobList -> value
    is AgentOptionWithValue.Boolean -> value
    is AgentOptionWithValue.Byte -> value
    is AgentOptionWithValue.ByteList -> value
    is AgentOptionWithValue.Double -> value
    is AgentOptionWithValue.DoubleList -> value
    is AgentOptionWithValue.Float -> value
    is AgentOptionWithValue.FloatList -> value
    is AgentOptionWithValue.Int -> value
    is AgentOptionWithValue.IntList -> value
    is AgentOptionWithValue.Long -> value
    is AgentOptionWithValue.LongList -> value
    is AgentOptionWithValue.Short -> value
    is AgentOptionWithValue.ShortList -> value
    is AgentOptionWithValue.String -> value
    is AgentOptionWithValue.StringList -> value
    is AgentOptionWithValue.UByte -> value
    is AgentOptionWithValue.UByteList -> value
    is AgentOptionWithValue.UInt -> value
    is AgentOptionWithValue.UIntList -> value
    is AgentOptionWithValue.ULong -> value
    is AgentOptionWithValue.ULongList -> value
    is AgentOptionWithValue.UShort -> value
    is AgentOptionWithValue.UShortList -> value
}

/**
 *  Extract the underlying option from an [AgentOptionWithValue] as a base [AgentOption].
 */
@Suppress("DuplicatedCode")
fun AgentOptionWithValue.option(): AgentOption = when (this) {
    is AgentOptionWithValue.Blob -> option
    is AgentOptionWithValue.BlobList -> option
    is AgentOptionWithValue.Boolean -> option
    is AgentOptionWithValue.Byte -> option
    is AgentOptionWithValue.ByteList -> option
    is AgentOptionWithValue.Double -> option
    is AgentOptionWithValue.DoubleList -> option
    is AgentOptionWithValue.Float -> option
    is AgentOptionWithValue.FloatList -> option
    is AgentOptionWithValue.Int -> option
    is AgentOptionWithValue.IntList -> option
    is AgentOptionWithValue.Long -> option
    is AgentOptionWithValue.LongList -> option
    is AgentOptionWithValue.Short -> option
    is AgentOptionWithValue.ShortList -> option
    is AgentOptionWithValue.String -> option
    is AgentOptionWithValue.StringList -> option
    is AgentOptionWithValue.UByte -> option
    is AgentOptionWithValue.UByteList -> option
    is AgentOptionWithValue.UInt -> option
    is AgentOptionWithValue.UIntList -> option
    is AgentOptionWithValue.ULong -> option
    is AgentOptionWithValue.ULongList -> option
    is AgentOptionWithValue.UShort -> option
    is AgentOptionWithValue.UShortList -> option
}

/**
 * Converts an AgentOptionWithValue to a string value that can be used to set an environment variable.
 * Use [AgentOptionWithValue.toDisplayString] if you intend to log the result; that function will censor secret data.
 */
fun AgentOptionWithValue.asEnvVarValue(): String = when (this) {
    is AgentOptionWithValue.Blob -> value().asEnvVarValue(true)
    is AgentOptionWithValue.BlobList -> value().asEnvVarValue(true)
    is AgentOptionWithValue.String -> value().asEnvVarValue(option.base64)
    is AgentOptionWithValue.StringList -> value().asEnvVarValue(option.base64)
    else -> value().asEnvVarValue()
}

/**
 * Writes the value of this option to file(s) using the values [AgentOptionValue.toFileSystemValue] function.  Note that
 * the return type is always a list.  For single value type options, a list with 1 value will be returned.  For list-type
 * options, a list of temporary files; one for every value in the option, will be returned.
 */
fun AgentOptionWithValue.toFileSystemValue(): List<Path> {
    return value().toFileSystemValue().map {
        val path = createTempFile(".opt")
        path.writeBytes(it)
        path
    }
}

/**
 * Prints the value of this option with secrets censored and with no Base64.  This function should only be used for
 * logging, this will the incorrect value for environment variables.
 */
fun AgentOptionWithValue.toDisplayString(): String = when (this) {
    is AgentOptionWithValue.Blob -> value.value.encodeBase64()
    is AgentOptionWithValue.BlobList -> value.value.joinToString(",") { it.encodeBase64() }
    is AgentOptionWithValue.Boolean -> if (value.value) { "1" } else { "0" }
    is AgentOptionWithValue.Byte -> value.value.toString()
    is AgentOptionWithValue.ByteList -> value.value.joinToString(",")
    is AgentOptionWithValue.Double -> value.value.toString()
    is AgentOptionWithValue.DoubleList -> value.value.joinToString(",")
    is AgentOptionWithValue.Float -> value.value.toString()
    is AgentOptionWithValue.FloatList -> value.value.joinToString(",")
    is AgentOptionWithValue.Int -> value.value.toString()
    is AgentOptionWithValue.IntList -> value.value.joinToString(",")
    is AgentOptionWithValue.Long -> value.value.toString()
    is AgentOptionWithValue.LongList -> value.value.joinToString(",")
    is AgentOptionWithValue.Short -> value.value.toString()
    is AgentOptionWithValue.ShortList -> value.value.joinToString(",")
    is AgentOptionWithValue.String -> {
        if (option.secret) {
            "*".repeat(value.value.length)
        }
        else {
            value.value
        }
    }
    is AgentOptionWithValue.StringList -> value.value.joinToString(",") {
        if (option.secret) {
            "*".repeat(it.length)
        }
        else {
            it
        }
    }
    is AgentOptionWithValue.UByte -> value.value.toString()
    is AgentOptionWithValue.UByteList -> value.value.joinToString(",")
    is AgentOptionWithValue.UInt -> value.value.toString()
    is AgentOptionWithValue.UIntList -> value.value.joinToString(",")
    is AgentOptionWithValue.ULong -> value.value.toString()
    is AgentOptionWithValue.ULongList -> value.value.joinToString(",")
    is AgentOptionWithValue.UShort -> value.value.toString()
    is AgentOptionWithValue.UShortList -> value.value.joinToString(",")
}

/**
 * If the option has a validation table, the require function will be called, validating the option's value with all the
 * criteria specified on the option's validation table.  If the value ends up being invalid, an [AgentOptionValidationException]
 * exception will be thrown.
 */
fun AgentOptionWithValue.requireValue() = when (this) {
    is AgentOptionWithValue.Byte -> option.validation?.require(value.value)
    is AgentOptionWithValue.ByteList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.Double -> option.validation?.require(value.value)
    is AgentOptionWithValue.DoubleList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.Float -> option.validation?.require(value.value)
    is AgentOptionWithValue.FloatList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.Int -> option.validation?.require(value.value)
    is AgentOptionWithValue.IntList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.Long -> option.validation?.require(value.value)
    is AgentOptionWithValue.LongList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.Short -> option.validation?.require(value.value)
    is AgentOptionWithValue.ShortList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.String -> option.validation?.require(value.value)
    is AgentOptionWithValue.StringList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.UByte -> option.validation?.require(value.value)
    is AgentOptionWithValue.UByteList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.UInt -> option.validation?.require(value.value)
    is AgentOptionWithValue.UIntList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.ULong -> option.validation?.require(value.value)
    is AgentOptionWithValue.ULongList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.UShort -> option.validation?.require(value.value)
    is AgentOptionWithValue.UShortList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.Blob -> option.validation?.require(value.value)
    is AgentOptionWithValue.BlobList -> value.value.forEach { option.validation?.require(it) }
    is AgentOptionWithValue.Boolean -> {
        // booleans have no validator
    }
}