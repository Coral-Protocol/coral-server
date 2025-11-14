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

        if (variants != null && !variants.isEmpty() && !variants.contains(value))
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
        if (variants != null && !variants.isEmpty() && !variants.contains(value))
            throw AgentOptionValidationException("Value $value is not a valid variant.  Valid variants are: ${variants.joinToString(",")})")
    }
}

@Serializable
data class ByteAgentOptionValidation(
    override val variants: List<Byte>?,
    override val min: Byte?,
    override val max: Byte?
) : NumericAgentOptionValidation<Byte>()

@Serializable
data class ShortAgentOptionValidation(
    override val variants: List<Short>?,
    override val min: Short?,
    override val max: Short?
) : NumericAgentOptionValidation<Short>()

@Serializable
data class IntAgentOptionValidation(
    override val variants: List<Int>?,
    override val min: Int?,
    override val max: Int?
) : NumericAgentOptionValidation<Int>()

@Serializable
data class LongAgentOptionValidation(
    override val variants: List<Long>?,
    override val min: Long?,
    override val max: Long?
) : NumericAgentOptionValidation<Long>()

@Serializable
data class UByteAgentOptionValidation(
    override val variants: List<UByte>?,
    override val min: UByte?,
    override val max: UByte?
) : NumericAgentOptionValidation<UByte>()

@Serializable
data class UShortAgentOptionValidation(
    override val variants: List<UShort>?,
    override val min: UShort?,
    override val max: UShort?
) : NumericAgentOptionValidation<UShort>()

@Serializable
data class UIntAgentOptionValidation(
    override val variants: List<UInt>?,
    override val min: UInt?,
    override val max: UInt?
) : NumericAgentOptionValidation<UInt>()

@Serializable
data class ULongAgentOptionValidation(
    override val variants: List<ULong>?,
    override val min: ULong?,
    override val max: ULong?
) : NumericAgentOptionValidation<ULong>()

@Serializable
data class FloatAgentOptionValidation(
    override val variants: List<Float>?,
    override val min: Float?,
    override val max: Float?
) : NumericAgentOptionValidation<Float>()

@Serializable
data class DoubleAgentOptionValidation(
    override val variants: List<Double>?,
    override val min: Double?,
    override val max: Double?
) : NumericAgentOptionValidation<Double>()
