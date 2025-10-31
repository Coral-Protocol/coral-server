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
data class NumericAgentOptionValidation<T: Comparable<T>>(
    val variants: List<T>? = null,
    val min: T? = null,
    val max: T? = null,
) {
    fun require(value: T) {
        if (min != null && value < min)
            throw AgentOptionValidationException("Value $value is less than the minimum value $min")

        if (max != null && value > max)
            throw AgentOptionValidationException("Value $value is greater than the maximum value $max")

        if (variants != null && !variants.contains(value))
            throw AgentOptionValidationException("Value $value is not a valid variant.  Valid variants are: ${variants.joinToString(",")})")
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

        if (variants != null && !variants.contains(value))
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