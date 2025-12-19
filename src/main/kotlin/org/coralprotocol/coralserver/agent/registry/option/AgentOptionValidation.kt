@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.option

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.exceptions.AgentOptionValidationException
import org.coralprotocol.coralserver.util.ByteUnitSizes
import org.coralprotocol.coralserver.util.toByteCount

@Serializable
data class ValidationFileSize(
    private val size: Double,
    private val unit: ByteUnitSizes
) {
    val byteCount: Long = unit.toByteCount(size)

    override fun toString(): String {
        return "$size $unit"
    }
}

@Serializable
data class StringAgentOptionValidation(
    val variants: List<String>? = null,

    @SerialName("min_length")
    val minLength: Int? = null,

    @SerialName("max_length")
    val maxLength: Int? = null,
    val regex: String? = null,
) {
    fun require(value: String) {
        if (minLength != null && value.length < minLength)
            throw AgentOptionValidationException("Value $value is shorter than the minimum length $minLength")

        if (maxLength != null && value.length > maxLength)
            throw AgentOptionValidationException("Value $value is longer than the maximum length $maxLength")

        if (regex != null && !value.matches(Regex(regex)))
            throw AgentOptionValidationException("Value $value does not match the regex pattern '$regex'")

        if (!variants.isNullOrEmpty() && !variants.contains(value))
            throw AgentOptionValidationException("Value $value is not a valid variant.  Valid variants are: ${variants.joinToString(",")})")
    }
}

@Serializable
data class BlobAgentOptionValidation(
    @SerialName("min_size")
    val minSize: ValidationFileSize? = null,

    @SerialName("max_size")
    val maxSize: ValidationFileSize? = null,
) {
    fun require(value: ByteArray) {
        if (minSize != null && value.size < minSize.byteCount)
            throw AgentOptionValidationException("Value $value is smaller than the minimum size ${minSize.byteCount} ($minSize)")

        if (maxSize != null && value.size > maxSize.byteCount)
            throw AgentOptionValidationException("Value $value is greater than the maximum size ${maxSize.byteCount} ($maxSize)")
    }
}

abstract class NumericAgentOptionValidation<T: Comparable<T>> {
    abstract val variants: List<T>?
    abstract val min: T?
    abstract val max: T?

    fun require(value: T) {
        val min = min
        if (min != null && value < min)
            throw AgentOptionValidationException("Value $value is less than the minimum value $min")

        val max = max
        if (max != null && value > max)
            throw AgentOptionValidationException("Value $value is greater than the maximum value $max")

        val variants = variants
        if (!variants.isNullOrEmpty() && !variants.contains(value))
            throw AgentOptionValidationException("Value $value is not a valid variant.  Valid variants are: ${variants.joinToString(",")})")
    }
}

@Serializable
data class ByteAgentOptionValidation(
    override val variants: List<Byte>? = null,
    override val min: Byte? = null,
    override val max: Byte? = null
) : NumericAgentOptionValidation<Byte>()

@Serializable
data class ShortAgentOptionValidation(
    override val variants: List<Short>? = null,
    override val min: Short? = null,
    override val max: Short? = null
) : NumericAgentOptionValidation<Short>()

@Serializable
data class IntAgentOptionValidation(
    override val variants: List<Int>? = null,
    override val min: Int? = null,
    override val max: Int? = null
) : NumericAgentOptionValidation<Int>()

@Serializable
data class LongAgentOptionValidation(
    override val variants: List<Long>? = null,
    override val min: Long? = null,
    override val max: Long? = null
) : NumericAgentOptionValidation<Long>()

@Serializable
data class UByteAgentOptionValidation(
    override val variants: List<UByte>? = null,
    override val min: UByte? = null,
    override val max: UByte? = null
) : NumericAgentOptionValidation<UByte>()

@Serializable
data class UShortAgentOptionValidation(
    override val variants: List<UShort>? = null,
    override val min: UShort? = null,
    override val max: UShort? = null
) : NumericAgentOptionValidation<UShort>()

@Serializable
data class UIntAgentOptionValidation(
    override val variants: List<UInt>? = null,
    override val min: UInt? = null,
    override val max: UInt? = null
) : NumericAgentOptionValidation<UInt>()

@Serializable
data class ULongAgentOptionValidation(
    override val variants: List<ULong>? = null,
    override val min: ULong? = null,
    override val max: ULong? = null
) : NumericAgentOptionValidation<ULong>()

@Serializable
data class FloatAgentOptionValidation(
    override val variants: List<Float>? = null,
    override val min: Float? = null,
    override val max: Float? = null
) : NumericAgentOptionValidation<Float>()

@Serializable
data class DoubleAgentOptionValidation(
    override val variants: List<Double>? = null,
    override val min: Double? = null,
    override val max: Double? = null
) : NumericAgentOptionValidation<Double>()