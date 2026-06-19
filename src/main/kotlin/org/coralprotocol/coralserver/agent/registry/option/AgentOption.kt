@file:OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.option

import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.coralprotocol.coralserver.agent.registry.RegistryAgentBase64StringListSerializer
import org.coralprotocol.coralserver.agent.registry.RegistryAgentBase64StringSerializer
import org.coralprotocol.coralserver.agent.registry.RegistryAgentStringListSerializer
import org.coralprotocol.coralserver.agent.registry.RegistryAgentStringSerializer
import org.coralprotocol.coralserver.util.decodeElement
import org.coralprotocol.coralserver.util.decodeFromElement
import org.coralprotocol.coralserver.util.encodeDiscriminatedElement
import org.koin.core.component.KoinComponent
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

typealias AgentOption = PolymorphicAgentOption<AgentOptionValue>

@Serializable(with = AgentOptionSerializer::class)
sealed interface PolymorphicAgentOption<out ValueType : PolymorphicAgentOptionValue<*>> : KoinComponent {
    val required: kotlin.Boolean
    val display: AgentOptionDisplay?
    val transport: AgentOptionTransport

    @Serializable
    @SerialName(TYPE_STRING)
    data class String(
        @Serializable(with = RegistryAgentStringSerializer::class)
        val default: kotlin.String? = null,

        val validation: StringAgentOptionValidation? = null,
        @Optional val base64: kotlin.Boolean = false,
        @Optional val secret: kotlin.Boolean = false,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.String>

    @Serializable
    @SerialName(TYPE_STRING_LIST)
    data class StringList(
        @Serializable(with = RegistryAgentStringListSerializer::class)
        @Optional val default: List<kotlin.String> = listOf(),

        val validation: StringAgentOptionValidation? = null,
        @Optional val base64: kotlin.Boolean = false,
        @Optional val secret: kotlin.Boolean = false,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.StringList>

    @Serializable
    @SerialName(TYPE_BLOB)
    data class Blob(
        @Serializable(with = RegistryAgentBase64StringSerializer::class)
        val default: kotlin.String? = null,

        val validation: BlobAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Blob> {
        @Transient
        val defaultBytes = default?.let { Base64.decode(it) }
    }

    @Serializable
    @SerialName(TYPE_BLOB_LIST)
    data class BlobList(
        @Serializable(with = RegistryAgentBase64StringListSerializer::class)
        @Optional val default: List<kotlin.String> = listOf(),

        val validation: BlobAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.BlobList> {
        @Transient
        val defaultBytes = default.map { Base64.decode(it) }
    }

    @Serializable
    @SerialName(TYPE_BOOLEAN)
    data class Boolean(
        val default: kotlin.Boolean? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Boolean>

    @Serializable
    @SerialName(TYPE_BYTE)
    data class Byte(
        val default: kotlin.Byte? = null,
        val validation: ByteAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Byte>

    @Serializable
    @SerialName(TYPE_BYTE_LIST)
    data class ByteList(
        @Optional val default: List<kotlin.Byte> = listOf(),
        val validation: ByteAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.ByteList>

    @Serializable
    @SerialName(TYPE_SHORT)
    data class Short(
        val default: kotlin.Short? = null,
        val validation: ShortAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Short>

    @Serializable
    @SerialName(TYPE_SHORT_LIST)
    data class ShortList(
        @Optional val default: List<kotlin.Short> = listOf(),
        val validation: ShortAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.ShortList>

    @Serializable
    @SerialName(TYPE_INT)
    data class Int(
        val default: kotlin.Int? = null,
        val validation: IntAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Int>

    @Serializable
    @SerialName(TYPE_INT_LIST)
    data class IntList(
        @Optional val default: List<kotlin.Int> = listOf(),
        val validation: IntAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.IntList>

    @Serializable
    @SerialName(TYPE_LONG)
    data class Long(
        val default: kotlin.Long? = null,
        val validation: LongAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Long>

    @Serializable
    @SerialName(TYPE_LONG_LIST)
    data class LongList(
        @Optional val default: List<kotlin.Long> = listOf(),
        val validation: LongAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.LongList>

    @Serializable
    @SerialName(TYPE_UNSIGNED_BYTE)
    data class UByte(
        val default: kotlin.UByte? = null,
        val validation: UByteAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UByte>

    @Serializable
    @SerialName(TYPE_UNSIGNED_BYTE_LIST)
    data class UByteList(
        @Optional val default: List<kotlin.UByte> = listOf(),
        val validation: UByteAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UByteList>

    @Serializable
    @SerialName(TYPE_UNSIGNED_SHORT)
    data class UShort(
        val default: kotlin.UShort? = null,
        val validation: UShortAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UShort>

    @Serializable
    @SerialName(TYPE_UNSIGNED_SHORT_LIST)
    data class UShortList(
        @Optional val default: List<kotlin.UShort> = listOf(),
        val validation: UShortAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UShortList>

    @Serializable
    @SerialName(TYPE_UNSIGNED_INT)
    data class UInt(
        val default: kotlin.UInt? = null,
        val validation: UIntAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UInt>

    @Serializable
    @SerialName(TYPE_UNSIGNED_INT_LIST)
    data class UIntList(
        @Optional val default: List<kotlin.UInt> = listOf(),
        val validation: UIntAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.UIntList>

    @Serializable
    @SerialName(TYPE_UNSIGNED_LONG)
    data class ULong(
        /**
         * OpenAPI does not support unsigned longs
         */
        val default: kotlin.String? = null,
        val validation: ULongAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.ULong>

    @Serializable
    @SerialName(TYPE_UNSIGNED_LONG_LIST)
    data class ULongList(
        /**
         * OpenAPI does not support unsigned longs
         */
        @Optional val default: List<kotlin.String> = listOf(),
        val validation: ULongAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.ULongList>

    @Serializable
    @SerialName(TYPE_FLOAT)
    data class Float(
        val default: kotlin.Float? = null,
        val validation: FloatAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Float>

    @Serializable
    @SerialName(TYPE_FLOAT_LIST)
    data class FloatList(
        @Optional val default: List<kotlin.Float> = listOf(),
        val validation: FloatAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.FloatList>

    @Serializable
    @SerialName(TYPE_DOUBLE)
    data class Double(
        val default: kotlin.Double? = null,
        val validation: DoubleAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.Double>

    @Serializable
    @SerialName(TYPE_DOUBLE_LIST)
    data class DoubleList(
        @Optional val default: List<kotlin.Double> = listOf(),
        val validation: DoubleAgentOptionValidation? = null,

        @Optional override val required: kotlin.Boolean = REQUIRED_DEFAULT,
        @Optional override val display: AgentOptionDisplay? = DISPLAY_DEFAULT,
        @Optional override val transport: AgentOptionTransport = TRANSPORT_DEFAULT,
    ) : PolymorphicAgentOption<PolymorphicAgentOptionValue.DoubleList>
}

fun PolymorphicAgentOption<*>.defaultAsValue(): PolymorphicAgentOptionValue<*>? =
    when (this) {
        is PolymorphicAgentOption.Blob -> this.default?.let { PolymorphicAgentOptionValue.Blob(it) }
        is PolymorphicAgentOption.BlobList -> PolymorphicAgentOptionValue.BlobList(this.default)
        is PolymorphicAgentOption.Boolean -> this.default?.let { PolymorphicAgentOptionValue.Boolean(it) }
        is PolymorphicAgentOption.Byte -> this.default?.let { PolymorphicAgentOptionValue.Byte(it) }
        is PolymorphicAgentOption.ByteList -> PolymorphicAgentOptionValue.ByteList(this.default)
        is PolymorphicAgentOption.Double -> this.default?.let { PolymorphicAgentOptionValue.Double(it) }
        is PolymorphicAgentOption.DoubleList -> PolymorphicAgentOptionValue.DoubleList(this.default)
        is PolymorphicAgentOption.Float -> this.default?.let { PolymorphicAgentOptionValue.Float(it) }
        is PolymorphicAgentOption.FloatList -> PolymorphicAgentOptionValue.FloatList(this.default)
        is PolymorphicAgentOption.Int -> this.default?.let { PolymorphicAgentOptionValue.Int(it) }
        is PolymorphicAgentOption.IntList -> PolymorphicAgentOptionValue.IntList(this.default)
        is PolymorphicAgentOption.Long -> this.default?.let { PolymorphicAgentOptionValue.Long(it) }
        is PolymorphicAgentOption.LongList -> PolymorphicAgentOptionValue.LongList(this.default)
        is PolymorphicAgentOption.Short -> this.default?.let { PolymorphicAgentOptionValue.Short(it) }
        is PolymorphicAgentOption.ShortList -> PolymorphicAgentOptionValue.ShortList(this.default)
        is PolymorphicAgentOption.String -> this.default?.let { PolymorphicAgentOptionValue.String(it) }
        is PolymorphicAgentOption.StringList -> PolymorphicAgentOptionValue.StringList(this.default)
        is PolymorphicAgentOption.UByte -> this.default?.let { PolymorphicAgentOptionValue.UByte(it) }
        is PolymorphicAgentOption.UByteList -> PolymorphicAgentOptionValue.UByteList(this.default)
        is PolymorphicAgentOption.UInt -> this.default?.let { PolymorphicAgentOptionValue.UInt(it) }
        is PolymorphicAgentOption.UIntList -> PolymorphicAgentOptionValue.UIntList(this.default)
        is PolymorphicAgentOption.ULong -> this.default?.let { PolymorphicAgentOptionValue.ULong(it) }
        is PolymorphicAgentOption.ULongList -> PolymorphicAgentOptionValue.ULongList(this.default)
        is PolymorphicAgentOption.UShort -> this.default?.let { PolymorphicAgentOptionValue.UShort(it) }
        is PolymorphicAgentOption.UShortList -> PolymorphicAgentOptionValue.UShortList(this.default)
    }

fun PolymorphicAgentOption<*>.withValue(value: PolymorphicAgentOptionValue<*>) =
    when (this) {
        is PolymorphicAgentOption.Blob -> AgentOptionWithValue.Blob(this, (value as PolymorphicAgentOptionValue.Blob))
        is PolymorphicAgentOption.BlobList -> AgentOptionWithValue.BlobList(
            this,
            (value as PolymorphicAgentOptionValue.BlobList)
        )

        is PolymorphicAgentOption.Boolean -> AgentOptionWithValue.Boolean(
            this,
            (value as PolymorphicAgentOptionValue.Boolean)
        )

        is PolymorphicAgentOption.Byte -> AgentOptionWithValue.Byte(this, (value as PolymorphicAgentOptionValue.Byte))
        is PolymorphicAgentOption.ByteList -> AgentOptionWithValue.ByteList(
            this,
            (value as PolymorphicAgentOptionValue.ByteList)
        )

        is PolymorphicAgentOption.Double -> AgentOptionWithValue.Double(
            this,
            (value as PolymorphicAgentOptionValue.Double)
        )

        is PolymorphicAgentOption.DoubleList -> AgentOptionWithValue.DoubleList(
            this,
            (value as PolymorphicAgentOptionValue.DoubleList)
        )

        is PolymorphicAgentOption.Float -> AgentOptionWithValue.Float(
            this,
            (value as PolymorphicAgentOptionValue.Float)
        )

        is PolymorphicAgentOption.FloatList -> AgentOptionWithValue.FloatList(
            this,
            (value as PolymorphicAgentOptionValue.FloatList)
        )

        is PolymorphicAgentOption.Int -> AgentOptionWithValue.Int(this, (value as PolymorphicAgentOptionValue.Int))
        is PolymorphicAgentOption.IntList -> AgentOptionWithValue.IntList(
            this,
            (value as PolymorphicAgentOptionValue.IntList)
        )

        is PolymorphicAgentOption.Long -> AgentOptionWithValue.Long(this, (value as PolymorphicAgentOptionValue.Long))
        is PolymorphicAgentOption.LongList -> AgentOptionWithValue.LongList(
            this,
            (value as PolymorphicAgentOptionValue.LongList)
        )

        is PolymorphicAgentOption.Short -> AgentOptionWithValue.Short(
            this,
            (value as PolymorphicAgentOptionValue.Short)
        )

        is PolymorphicAgentOption.ShortList -> AgentOptionWithValue.ShortList(
            this,
            (value as PolymorphicAgentOptionValue.ShortList)
        )

        is PolymorphicAgentOption.String -> AgentOptionWithValue.String(
            this,
            (value as PolymorphicAgentOptionValue.String)
        )

        is PolymorphicAgentOption.StringList -> AgentOptionWithValue.StringList(
            this,
            (value as PolymorphicAgentOptionValue.StringList)
        )

        is PolymorphicAgentOption.UByte -> AgentOptionWithValue.UByte(
            this,
            (value as PolymorphicAgentOptionValue.UByte)
        )

        is PolymorphicAgentOption.UByteList -> AgentOptionWithValue.UByteList(
            this,
            (value as PolymorphicAgentOptionValue.UByteList)
        )

        is PolymorphicAgentOption.UInt -> AgentOptionWithValue.UInt(this, (value as PolymorphicAgentOptionValue.UInt))
        is PolymorphicAgentOption.UIntList -> AgentOptionWithValue.UIntList(
            this,
            (value as PolymorphicAgentOptionValue.UIntList)
        )

        is PolymorphicAgentOption.ULong -> AgentOptionWithValue.ULong(
            this,
            (value as PolymorphicAgentOptionValue.ULong)
        )

        is PolymorphicAgentOption.ULongList -> AgentOptionWithValue.ULongList(
            this,
            (value as PolymorphicAgentOptionValue.ULongList)
        )

        is PolymorphicAgentOption.UShort -> AgentOptionWithValue.UShort(
            this,
            (value as PolymorphicAgentOptionValue.UShort)
        )

        is PolymorphicAgentOption.UShortList -> AgentOptionWithValue.UShortList(
            this,
            (value as PolymorphicAgentOptionValue.UShortList)
        )
    }

fun PolymorphicAgentOption<*>.compareTypeWithValue(value: PolymorphicAgentOptionValue<*>) =
    when (this) {
        is PolymorphicAgentOption.Blob -> value is PolymorphicAgentOptionValue.Blob
        is PolymorphicAgentOption.BlobList -> value is PolymorphicAgentOptionValue.BlobList
        is PolymorphicAgentOption.Boolean -> value is PolymorphicAgentOptionValue.Boolean
        is PolymorphicAgentOption.Byte -> value is PolymorphicAgentOptionValue.Byte
        is PolymorphicAgentOption.ByteList -> value is PolymorphicAgentOptionValue.ByteList
        is PolymorphicAgentOption.Double -> value is PolymorphicAgentOptionValue.Double
        is PolymorphicAgentOption.DoubleList -> value is PolymorphicAgentOptionValue.DoubleList
        is PolymorphicAgentOption.Float -> value is PolymorphicAgentOptionValue.Float
        is PolymorphicAgentOption.FloatList -> value is PolymorphicAgentOptionValue.FloatList
        is PolymorphicAgentOption.Int -> value is PolymorphicAgentOptionValue.Int
        is PolymorphicAgentOption.IntList -> value is PolymorphicAgentOptionValue.IntList
        is PolymorphicAgentOption.Long -> value is PolymorphicAgentOptionValue.Long
        is PolymorphicAgentOption.LongList -> value is PolymorphicAgentOptionValue.LongList
        is PolymorphicAgentOption.Short -> value is PolymorphicAgentOptionValue.Short
        is PolymorphicAgentOption.ShortList -> value is PolymorphicAgentOptionValue.ShortList
        is PolymorphicAgentOption.String -> value is PolymorphicAgentOptionValue.String
        is PolymorphicAgentOption.StringList -> value is PolymorphicAgentOptionValue.StringList
        is PolymorphicAgentOption.UByte -> value is PolymorphicAgentOptionValue.UByte
        is PolymorphicAgentOption.UByteList -> value is PolymorphicAgentOptionValue.UByteList
        is PolymorphicAgentOption.UInt -> value is PolymorphicAgentOptionValue.UInt
        is PolymorphicAgentOption.UIntList -> value is PolymorphicAgentOptionValue.UIntList
        is PolymorphicAgentOption.ULong -> value is PolymorphicAgentOptionValue.ULong
        is PolymorphicAgentOption.ULongList -> value is PolymorphicAgentOptionValue.ULongList
        is PolymorphicAgentOption.UShort -> value is PolymorphicAgentOptionValue.UShort
        is PolymorphicAgentOption.UShortList -> value is PolymorphicAgentOptionValue.UShortList
    }

fun PolymorphicAgentOption<*>.buildFullOption(
    name: String,
    description: String,
    required: Boolean
): Pair<String, PolymorphicAgentOption<*>> {
    val updatedOption = when (this) {
        is PolymorphicAgentOption.String -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.StringList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.Blob -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.BlobList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.Boolean -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.Byte -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.ByteList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.Short -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.ShortList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.Int -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.IntList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.Long -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.LongList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.UByte -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.UByteList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.UShort -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.UShortList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.UInt -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.UIntList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.ULong -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.ULongList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.Float -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.FloatList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.Double -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )

        is PolymorphicAgentOption.DoubleList -> this.copy(
            display = AgentOptionDisplay(description = description),
            required = required
        )
    }
    return name to updatedOption
}

fun PolymorphicAgentOption<*>.isIntegral() =
    when (this) {
        is PolymorphicAgentOption.Byte -> true
        is PolymorphicAgentOption.Int -> true
        is PolymorphicAgentOption.Long -> true
        is PolymorphicAgentOption.Short -> true
        is PolymorphicAgentOption.UByte -> true
        is PolymorphicAgentOption.UInt -> true
        is PolymorphicAgentOption.ULong -> true
        is PolymorphicAgentOption.UShort -> true
        else -> false
    }

fun PolymorphicAgentOption<*>.isFloat() =
    when (this) {
        is PolymorphicAgentOption.Float -> true
        is PolymorphicAgentOption.Double -> true
        else -> false
    }

@OptIn(InternalSerializationApi::class)
private val agentOptionSerializerMap: Map<String, KSerializer<out PolymorphicAgentOption<*>>> =
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
@OptIn(InternalSerializationApi::class)
class AgentOptionSerializer : KSerializer<AgentOption> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        "AgentOption",
        SerialKind.CONTEXTUAL
    )

    override fun serialize(
        encoder: Encoder,
        value: AgentOption
    ) {
        @Suppress("UNCHECKED_CAST")
        val serializer = value::class.serializer() as KSerializer<Any>
        encoder.encodeDiscriminatedElement(serializer, value, "type")
    }

    override fun deserialize(decoder: Decoder): AgentOption {
        val element = decoder.decodeElement()
        val type = element.takeDiscriminator("type")
        val serializer = agentOptionSerializerMap[type] ?: throw SerializationException("Unsupported type: $type")

        return decoder.decodeFromElement(serializer, element)
    }
}