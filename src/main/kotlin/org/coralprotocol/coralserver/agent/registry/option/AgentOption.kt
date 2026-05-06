@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.option

import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.ktor.util.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

@Serializable
@JsonClassDiscriminator("type")
sealed class AgentOption : KoinComponent {
    private val logger by inject<Logger>(named(LOGGER_CONFIG))

    @Optional
    var required: kotlin.Boolean = false
    var display: AgentOptionDisplay? = null

    @Optional
    var transport: AgentOptionTransport = AgentOptionTransport.ENVIRONMENT_VARIABLE

    /**
     * Called when the agent is resolved.  Issues warnings for bad configuration or use of deprecated fields.
     */
    fun issueConfigurationWarnings(edition: kotlin.Int, context: AgentResolutionContext, optionName: kotlin.String) {
        val locator = "Option '${optionName} in agent ${context.path}"

        if (required && defaultAsValue() != null)
            logger.warn { "$locator 'required = true' is not needed as the default value is set." }

        if ((this is String && base64 || this is StringList && base64) && transport == AgentOptionTransport.FILE_SYSTEM)
            logger.warn { "$locator has 'base64 = true' and 'transport = 'fs''.  The base64 field will be ignored" }

        // ugly just like the rest of AgentOption.*'s hideous mess of when statements!
        val emptyVariants = when (this) {
            is Byte -> validation?.variants?.isEmpty() ?: false
            is ByteList -> validation?.variants?.isEmpty() ?: false
            is Double -> validation?.variants?.isEmpty() ?: false
            is DoubleList -> validation?.variants?.isEmpty() ?: false
            is Float -> validation?.variants?.isEmpty() ?: false
            is FloatList -> validation?.variants?.isEmpty() ?: false
            is Int -> validation?.variants?.isEmpty() ?: false
            is IntList -> validation?.variants?.isEmpty() ?: false
            is Long -> validation?.variants?.isEmpty() ?: false
            is LongList -> validation?.variants?.isEmpty() ?: false
            is Short -> validation?.variants?.isEmpty() ?: false
            is ShortList -> validation?.variants?.isEmpty() ?: false
            is String -> validation?.variants?.isEmpty() ?: false
            is StringList -> validation?.variants?.isEmpty() ?: false
            is UByte -> validation?.variants?.isEmpty() ?: false
            is UByteList -> validation?.variants?.isEmpty() ?: false
            is UInt -> validation?.variants?.isEmpty() ?: false
            is UIntList -> validation?.variants?.isEmpty() ?: false
            is ULong -> validation?.variants?.isEmpty() ?: false
            is ULongList -> validation?.variants?.isEmpty() ?: false
            is UShort -> validation?.variants?.isEmpty() ?: false
            is UShortList -> validation?.variants?.isEmpty() ?: false
            else -> {
                // no variants
                false
            }
        }

        if (emptyVariants)
            logger.warn { "$locator has an empty variant list, this will match no values!  The variants field will be ignored" }
    }

    @Serializable
    @SerialName("string")
    data class String(
        @Serializable(with = RegistryAgentStringSerializer::class)
        val default: kotlin.String? = null,

        val validation: StringAgentOptionValidation? = null,
        @Optional val base64: kotlin.Boolean = false,
        @Optional val secret: kotlin.Boolean = false,
    ) : AgentOption()

    @Serializable
    @SerialName("list[string]")
    data class StringList(
        @Serializable(with = RegistryAgentStringListSerializer::class)
        @Optional val default: List<kotlin.String> = listOf(),

        val validation: StringAgentOptionValidation? = null,
        @Optional val base64: kotlin.Boolean = false,
        @Optional val secret: kotlin.Boolean = false
    ) : AgentOption()

    @Serializable
    @SerialName("blob")
    data class Blob(
        @Serializable(with = RegistryAgentBase64StringSerializer::class)
        val default: kotlin.String? = null,

        val validation: BlobAgentOptionValidation? = null
    ) : AgentOption() {
        @Transient
        val defaultBytes = default?.decodeBase64Bytes()
    }

    @Serializable
    @SerialName("list[blob]")
    data class BlobList(
        @Serializable(with = RegistryAgentBase64StringListSerializer::class)
        @Optional val default: List<kotlin.String> = listOf(),

        val validation: BlobAgentOptionValidation? = null
    ) : AgentOption() {
        @Transient
        val defaultBytes = default.map { it.decodeBase64Bytes() }
    }

    @Serializable
    @SerialName("bool")
    data class Boolean(
        val default: kotlin.Boolean? = null
    ) : AgentOption()

    @Serializable
    @SerialName("i8")
    data class Byte(
        val default: kotlin.Byte? = null,
        val validation: ByteAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[i8]")
    data class ByteList(
        @Optional val default: List<kotlin.Byte> = listOf(),
        val validation: ByteAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("i16")
    data class Short(
        val default: kotlin.Short? = null,
        val validation: ShortAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[i16]")
    data class ShortList(
        @Optional val default: List<kotlin.Short> = listOf(),
        val validation: ShortAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("i32")
    data class Int(
        val default: kotlin.Int? = null,
        val validation: IntAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[i32]")
    data class IntList(
        @Optional val default: List<kotlin.Int> = listOf(),
        val validation: IntAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("i64")
    data class Long(
        val default: kotlin.Long? = null,
        val validation: LongAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[i64]")
    data class LongList(
        @Optional val default: List<kotlin.Long> = listOf(),
        val validation: LongAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("u8")
    data class UByte(
        val default: kotlin.UByte? = null,
        val validation: UByteAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[u8]")
    data class UByteList(
        @Optional val default: List<kotlin.UByte> = listOf(),
        val validation: UByteAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("u16")
    data class UShort(
        val default: kotlin.UShort? = null,
        val validation: UShortAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[u16]")
    data class UShortList(
        @Optional val default: List<kotlin.UShort> = listOf(),
        val validation: UShortAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("u32")
    data class UInt(
        val default: kotlin.UInt? = null,
        val validation: UIntAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[u32]")
    data class UIntList(
        @Optional val default: List<kotlin.UInt> = listOf(),
        val validation: UIntAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("u64")
    data class ULong(
        /**
         * OpenAPI does not support unsigned longs
         */
        val default: kotlin.String? = null,
        val validation: ULongAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[u64]")
    data class ULongList(
        /**
         * OpenAPI does not support unsigned longs
         */
        @Optional val default: List<kotlin.String> = listOf(),
        val validation: ULongAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("f32")
    data class Float(
        val default: kotlin.Float? = null,
        val validation: FloatAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[f32]")
    data class FloatList(
        @Optional val default: List<kotlin.Float> = listOf(),
        val validation: FloatAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("f64")
    data class Double(
        val default: kotlin.Double? = null,
        val validation: DoubleAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[f64]")
    data class DoubleList(
        @Optional val default: List<kotlin.Double> = listOf(),
        val validation: DoubleAgentOptionValidation? = null
    ) : AgentOption()
}

fun AgentOption.defaultAsValue(): AgentOptionValue? =
    when (this) {
        is AgentOption.Blob -> this.default?.let { AgentOptionValue.Blob(it) }
        is AgentOption.BlobList -> AgentOptionValue.BlobList(this.default)
        is AgentOption.Boolean -> this.default?.let { AgentOptionValue.Boolean(it) }
        is AgentOption.Byte -> this.default?.let { AgentOptionValue.Byte(it) }
        is AgentOption.ByteList -> AgentOptionValue.ByteList(this.default)
        is AgentOption.Double -> this.default?.let { AgentOptionValue.Double(it) }
        is AgentOption.DoubleList -> AgentOptionValue.DoubleList(this.default)
        is AgentOption.Float -> this.default?.let { AgentOptionValue.Float(it) }
        is AgentOption.FloatList -> AgentOptionValue.FloatList(this.default)
        is AgentOption.Int -> this.default?.let { AgentOptionValue.Int(it) }
        is AgentOption.IntList -> AgentOptionValue.IntList(this.default)
        is AgentOption.Long -> this.default?.let { AgentOptionValue.Long(it) }
        is AgentOption.LongList -> AgentOptionValue.LongList(this.default)
        is AgentOption.Short -> this.default?.let { AgentOptionValue.Short(it) }
        is AgentOption.ShortList -> AgentOptionValue.ShortList(this.default)
        is AgentOption.String -> this.default?.let { AgentOptionValue.String(it) }
        is AgentOption.StringList -> AgentOptionValue.StringList(this.default)
        is AgentOption.UByte -> this.default?.let { AgentOptionValue.UByte(it) }
        is AgentOption.UByteList -> AgentOptionValue.UByteList(this.default)
        is AgentOption.UInt -> this.default?.let { AgentOptionValue.UInt(it) }
        is AgentOption.UIntList -> AgentOptionValue.UIntList(this.default)
        is AgentOption.ULong -> this.default?.let { AgentOptionValue.ULong(it) }
        is AgentOption.ULongList -> AgentOptionValue.ULongList(this.default)
        is AgentOption.UShort -> this.default?.let { AgentOptionValue.UShort(it) }
        is AgentOption.UShortList -> AgentOptionValue.UShortList(this.default)
    }

fun AgentOption.withValue(value: AgentOptionValue) =
    when (this) {
        is AgentOption.Blob -> AgentOptionWithValue.Blob(this, (value as AgentOptionValue.Blob))
        is AgentOption.BlobList -> AgentOptionWithValue.BlobList(this, (value as AgentOptionValue.BlobList))
        is AgentOption.Boolean -> AgentOptionWithValue.Boolean(this, (value as AgentOptionValue.Boolean))
        is AgentOption.Byte -> AgentOptionWithValue.Byte(this, (value as AgentOptionValue.Byte))
        is AgentOption.ByteList -> AgentOptionWithValue.ByteList(this, (value as AgentOptionValue.ByteList))
        is AgentOption.Double -> AgentOptionWithValue.Double(this, (value as AgentOptionValue.Double))
        is AgentOption.DoubleList -> AgentOptionWithValue.DoubleList(this, (value as AgentOptionValue.DoubleList))
        is AgentOption.Float -> AgentOptionWithValue.Float(this, (value as AgentOptionValue.Float))
        is AgentOption.FloatList -> AgentOptionWithValue.FloatList(this, (value as AgentOptionValue.FloatList))
        is AgentOption.Int -> AgentOptionWithValue.Int(this, (value as AgentOptionValue.Int))
        is AgentOption.IntList -> AgentOptionWithValue.IntList(this, (value as AgentOptionValue.IntList))
        is AgentOption.Long -> AgentOptionWithValue.Long(this, (value as AgentOptionValue.Long))
        is AgentOption.LongList -> AgentOptionWithValue.LongList(this, (value as AgentOptionValue.LongList))
        is AgentOption.Short -> AgentOptionWithValue.Short(this, (value as AgentOptionValue.Short))
        is AgentOption.ShortList -> AgentOptionWithValue.ShortList(this, (value as AgentOptionValue.ShortList))
        is AgentOption.String -> AgentOptionWithValue.String(this, (value as AgentOptionValue.String))
        is AgentOption.StringList -> AgentOptionWithValue.StringList(this, (value as AgentOptionValue.StringList))
        is AgentOption.UByte -> AgentOptionWithValue.UByte(this, (value as AgentOptionValue.UByte))
        is AgentOption.UByteList -> AgentOptionWithValue.UByteList(this, (value as AgentOptionValue.UByteList))
        is AgentOption.UInt -> AgentOptionWithValue.UInt(this, (value as AgentOptionValue.UInt))
        is AgentOption.UIntList -> AgentOptionWithValue.UIntList(this, (value as AgentOptionValue.UIntList))
        is AgentOption.ULong -> AgentOptionWithValue.ULong(this, (value as AgentOptionValue.ULong))
        is AgentOption.ULongList -> AgentOptionWithValue.ULongList(this, (value as AgentOptionValue.ULongList))
        is AgentOption.UShort -> AgentOptionWithValue.UShort(this, (value as AgentOptionValue.UShort))
        is AgentOption.UShortList -> AgentOptionWithValue.UShortList(this, (value as AgentOptionValue.UShortList))
    }

fun AgentOption.compareTypeWithValue(value: AgentOptionValue) =
    when (this) {
        is AgentOption.Blob -> value is AgentOptionValue.Blob
        is AgentOption.BlobList -> value is AgentOptionValue.BlobList
        is AgentOption.Boolean -> value is AgentOptionValue.Boolean
        is AgentOption.Byte -> value is AgentOptionValue.Byte
        is AgentOption.ByteList -> value is AgentOptionValue.ByteList
        is AgentOption.Double -> value is AgentOptionValue.Double
        is AgentOption.DoubleList -> value is AgentOptionValue.DoubleList
        is AgentOption.Float -> value is AgentOptionValue.Float
        is AgentOption.FloatList -> value is AgentOptionValue.FloatList
        is AgentOption.Int -> value is AgentOptionValue.Int
        is AgentOption.IntList -> value is AgentOptionValue.IntList
        is AgentOption.Long -> value is AgentOptionValue.Long
        is AgentOption.LongList -> value is AgentOptionValue.LongList
        is AgentOption.Short -> value is AgentOptionValue.Short
        is AgentOption.ShortList -> value is AgentOptionValue.ShortList
        is AgentOption.String -> value is AgentOptionValue.String
        is AgentOption.StringList -> value is AgentOptionValue.StringList
        is AgentOption.UByte -> value is AgentOptionValue.UByte
        is AgentOption.UByteList -> value is AgentOptionValue.UByteList
        is AgentOption.UInt -> value is AgentOptionValue.UInt
        is AgentOption.UIntList -> value is AgentOptionValue.UIntList
        is AgentOption.ULong -> value is AgentOptionValue.ULong
        is AgentOption.ULongList -> value is AgentOptionValue.ULongList
        is AgentOption.UShort -> value is AgentOptionValue.UShort
        is AgentOption.UShortList -> value is AgentOptionValue.UShortList
    }

fun AgentOption.buildFullOption(
    name: String,
    description: String,
    required: Boolean
): Pair<String, AgentOption> {
    this.display = AgentOptionDisplay(description = description)
    this.required = required
    return name to this
}

fun AgentOption.isIntegral() =
    when (this) {
        is AgentOption.Byte -> true
        is AgentOption.Int -> true
        is AgentOption.Long -> true
        is AgentOption.Short -> true
        is AgentOption.UByte -> true
        is AgentOption.UInt -> true
        is AgentOption.ULong -> true
        is AgentOption.UShort -> true
        else -> false
    }

fun AgentOption.isFloat() =
    when (this) {
        is AgentOption.Float -> true
        is AgentOption.Double -> true
        else -> false
    }

