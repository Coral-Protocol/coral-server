@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.openai

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("role")
@SerialName("OpenAIMessage")
sealed class Message {
    @Serializable
    @SerialName("developer")
    @Suppress("unused")
    data class SystemMessage(val content: List<SystemContent>, val name: String?) : Message()

    @Serializable
    @SerialName("user")
    @Suppress("unused")
    data class UserMessage(val content: List<UserContent>, val name: String? = null) : Message()

    @Serializable
    @SerialName("assistant")
    @Suppress("unused")
    data class AssistantMessage(
        val content: List<AssistantContent>,
        val refusal: String? = null,
        val audio: AudioAssistant? = null,
        val name: String? = null,
        val toolCalls: List<ToolCall>
    ) : Message()

    @Serializable
    @SerialName("tool")
    @Suppress("unused")
    data class ToolMessage(
        val toolCallId: String,
        val content: List<ToolResultContent>
    ) : Message()
}