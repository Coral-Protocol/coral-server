@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.generic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("role")
@SerialName("GenericMessage")
sealed class Message() {

    @Serializable
    @SerialName("user")
    @Suppress("unused")
    data class UserMessage(val content: List<UserContent>) : Message()

    @Serializable
    @SerialName("assistant")
    @Suppress("unused")
    data class AssistantMessage(
        val id: String? = null,
        val content: List<AssistantContent>,
    ) : Message()
}