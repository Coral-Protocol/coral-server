@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.option

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.registry.AgentResolutionContext
import org.coralprotocol.coralserver.agent.registry.CURRENT_AGENT_EDITION

private val logger = KotlinLogging.logger { }

@Serializable
@JsonClassDiscriminator("type")
sealed class AgentOption {
    val required: kotlin.Boolean = false
    var display: AgentOptionDisplay? = null
    val transport: AgentOptionTransport = AgentOptionTransport.ENVIRONMENT_VARIABLE

    /**
     * Description field should now be set in the display field class. This property is deprecated.
     * This remains for first edition agent definitions and may be removed in the future.
     */
    private val description: kotlin.String? = null

    init {
        if (display == null && description != null) {
            display = AgentOptionDisplay(description = description)
        }
    }

    /**
     * Called when the agent is resolved.  Issues warnings for bad configuration or use of deprecated fields.
     */
    fun issueConfigurationWarnings(edition: kotlin.Int, context: AgentResolutionContext, optionName: kotlin.String) {
        val locator = "Option '${optionName} in agent ${context.path}"

        // a warning is issued when the agent edition is less than CURRENT_AGENT_EDITION, so there is not much point
        // generating further warnings here if, for example, edition 2 features are used in an agent definition of
        // edition 1
        if (edition != CURRENT_AGENT_EDITION)
            return

        if (description != null) {
            logger.warn { """
                $locator has deprecated 'description' field.  Agent option description should now be set under the 'display' table, for example:
                
                [display]:
                description: "$description"
            """.trimIndent() }
        }

        if (this is Number)
            logger.warn { "$locator has deprecated 'number' type.  In edition $edition the 'number' type was renamed to 'f64'." }

        if (this is Secret) {
            logger.warn { """
                $locator has deprecated 'secret' type.  In edition $edition the 'secret' has been changed to:
                
                type = "string"
                secret = true
            """.trimIndent() }
        }

        if (required && defaultAsValue() != null)
            logger.warn { "$locator 'required = true' is not needed as the default value is set." }

        if (this is String || this is StringList && base64 && transport == AgentOptionTransport.FILE_SYSTEM)
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
            is Number -> validation?.variants?.isEmpty() ?: false
            is Secret -> validation?.variants?.isEmpty() ?: false
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
        val default: kotlin.String? = null,
        val validation: StringAgentOptionValidation? = null,
        val base64: kotlin.Boolean = false,
        val secret: kotlin.Boolean = false,
    ) : AgentOption()

    @Serializable
    @SerialName("list[string]")
    data class StringList(
        val default: List<kotlin.String>? = null,
        val validation: StringAgentOptionValidation? = null,
        val base64: kotlin.Boolean = false,
        val secret: kotlin.Boolean = false
    ) : AgentOption()

    @Serializable
    @SerialName("blob")
    data class Blob(
        @Suppress("ArrayInDataClass") val default: ByteArray? = null,
        val validation: BlobAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[blob]")
    data class BlobList(
        val default: List<ByteArray>? = null,
        val validation: BlobAgentOptionValidation? = null
    ) : AgentOption()

    @Serializable
    @SerialName("bool")
    data class Boolean(
        val default: kotlin.Boolean? = null
    ) : AgentOption()

    @Serializable
    @SerialName("i8")
    data class Byte(
        val default: kotlin.Byte? = null,
        val validation: NumericAgentOptionValidation<kotlin.Byte>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[i8]")
    data class ByteList(
        val default: List<kotlin.Byte>? = null,
        val validation: NumericAgentOptionValidation<kotlin.Byte>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("i16")
    data class Short(
        val default: kotlin.Short? = null,
        val validation: NumericAgentOptionValidation<kotlin.Short>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[i16]")
    data class ShortList(
        val default: List<kotlin.Short>? = null,
        val validation: NumericAgentOptionValidation<kotlin.Short>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("i32")
    data class Int(
        val default: kotlin.Int? = null,
        val validation: NumericAgentOptionValidation<kotlin.Int>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[i32]")
    data class IntList(
        val default: List<kotlin.Int>? = null,
        val validation: NumericAgentOptionValidation<kotlin.Int>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("i64")
    data class Long(
        val default: kotlin.Long? = null,
        val validation: NumericAgentOptionValidation<kotlin.Long>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[i64]")
    data class LongList(
        val default: List<kotlin.Long>? = null,
        val validation: NumericAgentOptionValidation<kotlin.Long>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("u8")
    data class UByte(
        val default: kotlin.UByte? = null,
        val validation: NumericAgentOptionValidation<kotlin.UByte>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[u8]")
    data class UByteList(
        val default: List<kotlin.UByte>? = null,
        val validation: NumericAgentOptionValidation<kotlin.UByte>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("u16")
    data class UShort(
        val default: kotlin.UShort? = null,
        val validation: NumericAgentOptionValidation<kotlin.UShort>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[u16]")
    data class UShortList(
        val default: List<kotlin.UShort>? = null,
        val validation: NumericAgentOptionValidation<kotlin.UShort>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("u32")
    data class UInt(
        val default: kotlin.UInt? = null,
        val validation: NumericAgentOptionValidation<kotlin.UInt>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[u32]")
    data class UIntList(
        val default: List<kotlin.UInt>? = null,
        val validation: NumericAgentOptionValidation<kotlin.UInt>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("u64")
    data class ULong(
        val default: kotlin.ULong? = null,
        val validation: NumericAgentOptionValidation<kotlin.ULong>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[u64]")
    data class ULongList(
        val default: List<kotlin.ULong>? = null,
        val validation: NumericAgentOptionValidation<kotlin.ULong>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("f32")
    data class Float(
        val default: kotlin.Float? = null,
        val validation: NumericAgentOptionValidation<kotlin.Float>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[f32]")
    data class FloatList(
        val default: List<kotlin.Float>? = null,
        val validation: NumericAgentOptionValidation<kotlin.Float>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("f64")
    data class Double(
        val default: kotlin.Double? = null,
        val validation: NumericAgentOptionValidation<kotlin.Double>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("list[f64]")
    data class DoubleList(
        val default: List<kotlin.Double>? = null,
        val validation: NumericAgentOptionValidation<kotlin.Double>? = null
    ) : AgentOption()

    /*
        Edition 1 backwards compatibility options
     */

    @Serializable
    @SerialName("number")
    data class Number(
        val default: kotlin.Double? = null,
        val validation: NumericAgentOptionValidation<kotlin.Double>? = null
    ) : AgentOption()

    @Serializable
    @SerialName("secret")
    data class Secret(
        val default: kotlin.String? = null,
        val validation: StringAgentOptionValidation? = null
    ) : AgentOption()
}

fun AgentOption.defaultAsValue(): AgentOptionValue? =
    when (this) {
        is AgentOption.Blob -> this.default?.let { AgentOptionValue.Blob(it) }
        is AgentOption.BlobList -> this.default?.let { AgentOptionValue.BlobList(it) }
        is AgentOption.Boolean -> this.default?.let { AgentOptionValue.Boolean(it) }
        is AgentOption.Byte -> this.default?.let { AgentOptionValue.Byte(it) }
        is AgentOption.ByteList -> this.default?.let { AgentOptionValue.ByteList(it) }
        is AgentOption.Double -> this.default?.let { AgentOptionValue.Double(it) }
        is AgentOption.DoubleList -> this.default?.let { AgentOptionValue.DoubleList(it) }
        is AgentOption.Float -> this.default?.let { AgentOptionValue.Float(it) }
        is AgentOption.FloatList -> this.default?.let { AgentOptionValue.FloatList(it) }
        is AgentOption.Int -> this.default?.let { AgentOptionValue.Int(it) }
        is AgentOption.IntList -> this.default?.let { AgentOptionValue.IntList(it) }
        is AgentOption.Long -> this.default?.let { AgentOptionValue.Long(it) }
        is AgentOption.LongList -> this.default?.let { AgentOptionValue.LongList(it) }
        is AgentOption.Short -> this.default?.let { AgentOptionValue.Short(it) }
        is AgentOption.ShortList -> this.default?.let { AgentOptionValue.ShortList(it) }
        is AgentOption.String -> this.default?.let { AgentOptionValue.String(it) }
        is AgentOption.StringList -> this.default?.let { AgentOptionValue.StringList(it) }
        is AgentOption.UByte -> this.default?.let { AgentOptionValue.UByte(it) }
        is AgentOption.UByteList -> this.default?.let { AgentOptionValue.UByteList(it) }
        is AgentOption.UInt -> this.default?.let { AgentOptionValue.UInt(it) }
        is AgentOption.UIntList -> this.default?.let { AgentOptionValue.UIntList(it) }
        is AgentOption.ULong -> this.default?.let { AgentOptionValue.ULong(it) }
        is AgentOption.ULongList -> this.default?.let { AgentOptionValue.ULongList(it) }
        is AgentOption.UShort -> this.default?.let { AgentOptionValue.UShort(it) }
        is AgentOption.UShortList -> this.default?.let { AgentOptionValue.UShortList(it) }
        is AgentOption.Number -> this.default?.let { AgentOptionValue.Double(it) }
        is AgentOption.Secret -> this.default?.let { AgentOptionValue.String(it) }
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

        // Backwards compatibility: normalize to double
        is AgentOption.Number -> {
            AgentOptionWithValue.Double(AgentOption.Double(
                default = this.default,
                validation = this.validation
            ), (value as AgentOptionValue.Double))
        }

        // Backwards compatibility: normalize to string
        is AgentOption.Secret -> {
            AgentOptionWithValue.String(AgentOption.String(
                default = this.default,
                validation = this.validation,
                secret = true
            ), (value as AgentOptionValue.String))
        }
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
        is AgentOption.Number -> value is AgentOptionValue.Double
        is AgentOption.Secret -> value is AgentOptionValue.String
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