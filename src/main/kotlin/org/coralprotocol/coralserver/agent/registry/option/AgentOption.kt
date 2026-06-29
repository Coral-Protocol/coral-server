@file:OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.option

import dev.eav.tomlkt.TomlClassDiscriminator
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.exceptions.AgentOptionValidationException
import org.coralprotocol.coralserver.agent.registry.RegistryAgentBase64StringListSerializer
import org.coralprotocol.coralserver.agent.registry.RegistryAgentBase64StringSerializer
import org.coralprotocol.coralserver.agent.registry.RegistryAgentStringListSerializer
import org.coralprotocol.coralserver.agent.registry.RegistryAgentStringSerializer
import org.coralprotocol.coralserver.util.decodeElement
import org.coralprotocol.coralserver.util.decodeFromElement
import org.coralprotocol.coralserver.util.encodeDiscriminatedElement
import kotlin.io.encoding.Base64

const val TYPE_STRING = "string"
const val TYPE_STRING_LIST = "list[string]"
const val TYPE_BLOB = "blob"
const val TYPE_BLOB_LIST = "list[blob]"
const val TYPE_BOOLEAN = "bool"
const val TYPE_BYTE = "i8"
const val TYPE_BYTE_LIST = "list[i8]"
const val TYPE_SHORT = "i16"
const val TYPE_SHORT_LIST = "list[i16]"
const val TYPE_INT = "i32"
const val TYPE_INT_LIST = "list[i32]"
const val TYPE_LONG = "i64"
const val TYPE_LONG_LIST = "list[i64]"
const val TYPE_UNSIGNED_BYTE = "u8"
const val TYPE_UNSIGNED_BYTE_LIST = "list[u8]"
const val TYPE_UNSIGNED_SHORT = "u16"
const val TYPE_UNSIGNED_SHORT_LIST = "list[u16]"
const val TYPE_UNSIGNED_INT = "u32"
const val TYPE_UNSIGNED_INT_LIST = "list[u32]"
const val TYPE_UNSIGNED_LONG = "u64"
const val TYPE_UNSIGNED_LONG_LIST = "list[u64]"
const val TYPE_FLOAT = "f32"
const val TYPE_FLOAT_LIST = "list[f32]"
const val TYPE_DOUBLE = "f64"
const val TYPE_DOUBLE_LIST = "list[f64]"

private const val REQUIRED_DEFAULT = false
private val DISPLAY_DEFAULT: AgentOptionDisplay? = null
private val TRANSPORT_DEFAULT = AgentOptionTransport.ENVIRONMENT_VARIABLE

interface AgentIntegralOption
typealias AgentOption = PolymorphicAgentOption<*, *>

sealed interface PolymorphicAgentOption<ValueType : PolymorphicAgentOptionValue<BackingType>, BackingType> {
    val default: BackingType?
    val defaultAsValue: ValueType?

    val required: kotlin.Boolean
    val display: AgentOptionDisplay?
    val transport: AgentOptionTransport

    /**
     * Attempts to create a [AgentOptionWithValue] type using the specified value, returning null if the specified value
     * type is mismatched.
     */
    fun tryWithValue(value: PolymorphicAgentOptionValue<*>): AgentOptionWithValue<*, ValueType, BackingType>?

    /**
     * Runs the validation functions for this option, throwing an exception if any validation fails.
     */
    fun validateValue(value: ValueType)

    /**
     * Returns a string representation of the specified value.  This will mask values marked as secret.
     */
    fun displayValue(value: ValueType): kotlin.String

    /**
     * Returns a [AgentOptionWithValue] with the default value, or null if there is no default value.
     */
    fun withDefaultValue() = defaultAsValue?.let { AgentOptionWithValue(this, it) }

    /**
     * Combines this option with the specified value, returning a [AgentOptionWithValue]
     */
    fun withValue(value: ValueType) = AgentOptionWithValue(this, value)

    @Serializable
    @SerialName(TYPE_STRING)
    data class String(
        @Serializable(with = RegistryAgentStringSerializer::class)
        override val default: kotlin.String? = null,

        val validation: StringAgentOptionValidation? = null,
        @Optional val base64: kotlin.Boolean = false,
        @Optional val secret: kotlin.Boolean = false,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.String, kotlin.String> {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.String(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.String)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.String) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.String): kotlin.String =
            if (secret) {
                "*".repeat(value.value.length)
            } else {
                value.value
            }
    }

    @Serializable
    @SerialName(TYPE_STRING_LIST)
    data class StringList(
        @Serializable(with = RegistryAgentStringListSerializer::class)
        @Optional override val default: List<kotlin.String> = listOf(),

        val validation: StringAgentOptionValidation? = null,
        @Optional val base64: kotlin.Boolean = false,
        @Optional val secret: kotlin.Boolean = false,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.StringList, List<kotlin.String>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.StringList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.StringList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.StringList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.StringList): kotlin.String =
            value.value.joinToString(",") {
                if (secret) {
                    "*".repeat(it.length)
                } else {
                    it
                }
            }
    }

    @Serializable
    @SerialName(TYPE_BLOB)
    data class Blob(
        @Serializable(with = RegistryAgentBase64StringSerializer::class)
        override val default: kotlin.String? = null,

        val validation: BlobAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Blob, kotlin.String> {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.Blob(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.Blob)?.let { AgentOptionWithValue(this, it) }

        @Transient
        val defaultBytes = default?.let { Base64.decode(it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.Blob) {
            validation?.require(value.bytes)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.Blob): kotlin.String =
            "${value.bytes.size}b blob"
    }

    @Serializable
    @SerialName(TYPE_BLOB_LIST)
    data class BlobList(
        @Serializable(with = RegistryAgentBase64StringListSerializer::class)
        @Optional override val default: List<kotlin.String> = listOf(),

        val validation: BlobAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.BlobList, List<kotlin.String>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.BlobList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.BlobList)?.let { AgentOptionWithValue(this, it) }

        @Transient
        val defaultBytes = default.map { Base64.decode(it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.BlobList) =
            value.bytes.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.BlobList): kotlin.String =
            value.bytes.joinToString(",") { "${it.size}b blob" }
    }

    @Serializable
    @SerialName(TYPE_BOOLEAN)
    data class Boolean(
        override val default: kotlin.Boolean? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Boolean, kotlin.Boolean> {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.Boolean(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.Boolean)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.Boolean) = Unit

        override fun displayValue(value: PolymorphicAgentOptionValue.Boolean): kotlin.String =
            if (value.value) "1" else "0"
    }

    @Serializable
    @SerialName(TYPE_BYTE)
    data class Byte(
        override val default: kotlin.Byte? = null,
        val validation: ByteAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Byte, kotlin.Byte>, AgentIntegralOption {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.Byte(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.Byte)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.Byte) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.Byte): kotlin.String =
            value.value.toString()
    }

    @Serializable
    @SerialName(TYPE_BYTE_LIST)
    data class ByteList(
        @Optional override val default: List<kotlin.Byte> = listOf(),
        val validation: ByteAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.ByteList, List<kotlin.Byte>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.ByteList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.ByteList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.ByteList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.ByteList): kotlin.String =
            value.value.joinToString(",")
    }

    @Serializable
    @SerialName(TYPE_SHORT)
    data class Short(
        override val default: kotlin.Short? = null,
        val validation: ShortAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Short, kotlin.Short>, AgentIntegralOption {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.Short(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.Short)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.Short) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.Short): kotlin.String =
            value.value.toString()
    }

    @Serializable
    @SerialName(TYPE_SHORT_LIST)
    data class ShortList(
        @Optional override val default: List<kotlin.Short> = listOf(),
        val validation: ShortAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.ShortList, List<kotlin.Short>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.ShortList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.ShortList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.ShortList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.ShortList): kotlin.String =
            value.value.joinToString(",")
    }

    @Serializable
    @SerialName(TYPE_INT)
    data class Int(
        override val default: kotlin.Int? = null,
        val validation: IntAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Int, kotlin.Int>, AgentIntegralOption {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.Int(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.Int)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.Int) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.Int): kotlin.String =
            value.value.toString()
    }

    @Serializable
    @SerialName(TYPE_INT_LIST)
    data class IntList(
        @Optional override val default: List<kotlin.Int> = listOf(),
        val validation: IntAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.IntList, List<kotlin.Int>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.IntList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.IntList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.IntList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.IntList): kotlin.String =
            value.value.joinToString(",")
    }

    @Serializable
    @SerialName(TYPE_LONG)
    data class Long(
        override val default: kotlin.Long? = null,
        val validation: LongAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Long, kotlin.Long>, AgentIntegralOption {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.Long(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.Long)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.Long) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.Long): kotlin.String =
            value.value.toString()
    }

    @Serializable
    @SerialName(TYPE_LONG_LIST)
    data class LongList(
        @Optional override val default: List<kotlin.Long> = listOf(),
        val validation: LongAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.LongList, List<kotlin.Long>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.LongList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.LongList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.LongList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.LongList): kotlin.String =
            value.value.joinToString(",")
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_BYTE)
    data class UByte(
        override val default: kotlin.UByte? = null,
        val validation: UByteAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UByte, kotlin.UByte>, AgentIntegralOption {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.UByte(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.UByte)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.UByte) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.UByte): kotlin.String =
            value.value.toString()
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_BYTE_LIST)
    data class UByteList(
        @Optional override val default: List<kotlin.UByte> = listOf(),
        val validation: UByteAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UByteList, List<kotlin.UByte>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.UByteList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.UByteList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.UByteList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.UByteList): kotlin.String =
            value.value.joinToString(",")
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_SHORT)
    data class UShort(
        override val default: kotlin.UShort? = null,
        val validation: UShortAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UShort, kotlin.UShort>, AgentIntegralOption {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.UShort(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.UShort)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.UShort) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.UShort): kotlin.String =
            value.value.toString()
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_SHORT_LIST)
    data class UShortList(
        @Optional override val default: List<kotlin.UShort> = listOf(),
        val validation: UShortAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UShortList, List<kotlin.UShort>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.UShortList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.UShortList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.UShortList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.UShortList): kotlin.String =
            value.value.joinToString(",")
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_INT)
    data class UInt(
        override val default: kotlin.UInt? = null,
        val validation: UIntAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UInt, kotlin.UInt>, AgentIntegralOption {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.UInt(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.UInt)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.UInt) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.UInt): kotlin.String =
            value.value.toString()
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_INT_LIST)
    data class UIntList(
        @Optional override val default: List<kotlin.UInt> = listOf(),
        val validation: UIntAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UIntList, List<kotlin.UInt>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.UIntList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.UIntList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.UIntList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.UIntList): kotlin.String =
            value.value.joinToString(",")
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_LONG)
    data class ULong(
        /**
         * OpenAPI does not support unsigned longs
         */
        override val default: kotlin.String? = null,
        val validation: ULongAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.ULong, kotlin.String>, AgentIntegralOption {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.ULong(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.ULong)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.ULong) {
            validation?.require(
                value.value.toULongOrNull()
                    ?: throw AgentOptionValidationException("${value.value} is not a valid u64")
            )
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.ULong): kotlin.String =
            value.value
    }

    @Serializable
    @SerialName(TYPE_UNSIGNED_LONG_LIST)
    data class ULongList(
        /**
         * OpenAPI does not support unsigned longs
         */
        @Optional override val default: List<kotlin.String> = listOf(),
        val validation: ULongAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.ULongList, List<kotlin.String>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.ULongList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.ULongList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.ULongList) =
            value.value.forEach {
                validation?.require(
                    it.toULongOrNull()
                        ?: throw AgentOptionValidationException("${value.value} is not a valid u64")
                )
            }

        override fun displayValue(value: PolymorphicAgentOptionValue.ULongList): kotlin.String =
            value.value.joinToString(",")
    }

    @Serializable
    @SerialName(TYPE_FLOAT)
    data class Float(
        override val default: kotlin.Float? = null,
        val validation: FloatAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Float, kotlin.Float> {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.Float(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.Float)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.Float) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.Float): kotlin.String =
            value.value.toString()
    }

    @Serializable
    @SerialName(TYPE_FLOAT_LIST)
    data class FloatList(
        @Optional override val default: List<kotlin.Float> = listOf(),
        val validation: FloatAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.FloatList, List<kotlin.Float>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.FloatList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.FloatList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.FloatList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.FloatList): kotlin.String =
            value.value.joinToString(",")
    }

    @Serializable
    @SerialName(TYPE_DOUBLE)
    data class Double(
        override val default: kotlin.Double? = null,
        val validation: DoubleAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Double, kotlin.Double> {
        @Transient
        override val defaultAsValue = default?.let { PolymorphicAgentOptionValue.Double(it) }

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.Double)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.Double) {
            validation?.require(value.value)
        }

        override fun displayValue(value: PolymorphicAgentOptionValue.Double): kotlin.String =
            value.value.toString()
    }

    @Serializable
    @SerialName(TYPE_DOUBLE_LIST)
    data class DoubleList(
        @Optional override val default: List<kotlin.Double> = listOf(),
        val validation: DoubleAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.DoubleList, List<kotlin.Double>> {
        @Transient
        override val defaultAsValue = PolymorphicAgentOptionValue.DoubleList(default)

        override fun tryWithValue(value: PolymorphicAgentOptionValue<*>) =
            (value as? PolymorphicAgentOptionValue.DoubleList)?.let { AgentOptionWithValue(this, it) }

        override fun validateValue(value: PolymorphicAgentOptionValue.DoubleList) =
            value.value.forEach { validation?.require(it) }

        override fun displayValue(value: PolymorphicAgentOptionValue.DoubleList): kotlin.String =
            value.value.joinToString(",")
    }
}

fun AgentOption.isIntegral() =
    this is AgentIntegralOption

@OptIn(InternalSerializationApi::class)
private val agentOptionSerializerMap: Map<String, KSerializer<out PolymorphicAgentOption<*, *>>> =
    PolymorphicAgentOption::class.sealedSubclasses.associate { kClass ->
        val serializer = kClass.serializer()
        serializer.descriptor.serialName to serializer
    }

/**
 * Kotlinx won't serialize sealed classes that are generic with star projection.  [PolymorphicAgentOption] is a
 * sealed generic class but the type parameter does not affect serialization.  This custom serializer pretty much does
 * what the generated one would have without throwing errors for star projection.
 *
 * Note this is also required for [PolymorphicAgentOptionValue] because it is a sealed generic class.
 */
class AgentOptionSerializer : KSerializer<AgentOption> {
    private val discriminatorName = "type"

    // Same descriptor generated by SealedClassSerializer
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        "AgentOption",
        PolymorphicKind.SEALED
    ) {
        annotations = listOf(JsonClassDiscriminator(discriminatorName), TomlClassDiscriminator(discriminatorName))

        element(discriminatorName, String.serializer().descriptor)
        element(
            "value",
            buildSerialDescriptor("kotlinx.serialization.Sealed<AgentOption>", SerialKind.CONTEXTUAL) {
                agentOptionSerializerMap.forEach { (name, serializer) ->
                    element(name, serializer.descriptor)
                }
            })
    }

    override fun serialize(
        encoder: Encoder,
        value: AgentOption
    ) {
        @Suppress("UNCHECKED_CAST")
        val serializer = value::class.serializer() as KSerializer<Any>
        encoder.encodeDiscriminatedElement(serializer, value, discriminatorName)
    }

    override fun deserialize(decoder: Decoder): AgentOption {
        val element = decoder.decodeElement()
        val type = element.takeDiscriminator(discriminatorName)
        val serializer = agentOptionSerializerMap[type] ?: throw SerializationException("Unsupported type: $type")

        return decoder.decodeFromElement(serializer, element)
    }
}

/**
 * Currently (Kotlinx 1.10.0, Kotlin 2.3.20) there is a bug where @Serializable(with = AgentOptionSerializer::class)
 * is not picked up, causing compilation errors.  This is likely a compiler bug.  This type is only serialized in maps,
 * so we can avoid the bug by using a custom map serializer in addition to [AgentOptionSerializer]
 */
class AgentOptionSerializerMap : KSerializer<Map<String, AgentOption>> {
    private val keySerializer = String.serializer()
    private val valueSerializer = AgentOptionSerializer()

    override val descriptor: SerialDescriptor = mapSerialDescriptor(
        keySerializer.descriptor,
        valueSerializer.descriptor
    )

    override fun serialize(encoder: Encoder, value: Map<String, AgentOption>) {
        val mapEncoder = encoder.beginStructure(descriptor)
        var index = 0
        value.forEach { (key, value) ->
            mapEncoder.encodeSerializableElement(descriptor, index++, keySerializer, key)
            mapEncoder.encodeSerializableElement(descriptor, index++, valueSerializer, value)
        }
        mapEncoder.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Map<String, AgentOption> {
        val mapDecoder = decoder.beginStructure(descriptor)
        val result = mutableMapOf<String, AgentOption>()

        while (true) {
            val index = mapDecoder.decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break

            val key = mapDecoder.decodeSerializableElement(descriptor, index, keySerializer)
            val valueIndex = mapDecoder.decodeElementIndex(descriptor)
            val value = mapDecoder.decodeSerializableElement(descriptor, valueIndex, valueSerializer)
            result[key] = value
        }

        mapDecoder.endStructure(descriptor)
        return result
    }
}