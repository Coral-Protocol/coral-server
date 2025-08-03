@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.generic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.models.serialization.OneOrMany

@Serializable
@JsonClassDiscriminator("message_discrim")
sealed class Message(val message_discrim: String) {

    @Serializable
    @SerialName("user")
    @Suppress("unused")
    data class UserMessage(val content: UserContent) : Message("user")

    @Serializable
    @SerialName("assistant")
    @Suppress("unused")
    data class AssistantMessage(
        val id: String? = null,
        val content: AssistantContent,
    ) : Message("assistant")
}