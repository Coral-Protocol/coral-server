@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed class AgentOptionValue {

    @Serializable
    @SerialName("string")
    data class String(val value: kotlin.String) : AgentOptionValue()

    @Serializable
    @SerialName("number")
    data class Number(val value: Double) : AgentOptionValue()
}