@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry.option

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NumericAgentOptionValidation<T>(
    val variants: List<T> = listOf(),
    val min: T? = null,
    val max: T? = null,
)

@Serializable
data class StringAgentOptionValidation(
    val variants: List<String> = listOf(),

    @SerialName("min_length")
    val minLength: Int? = null,

    @SerialName("max_length")
    val maxLength: Int? = null,
    val regex: String? = null,
)