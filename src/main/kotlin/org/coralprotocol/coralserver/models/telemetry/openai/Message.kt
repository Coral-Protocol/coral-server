@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.openai

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.models.serialization.OneOrMany

@Serializable
@JsonClassDiscriminator("role")
sealed class Message {
    @Serializable
    @SerialName("developer")
    @Suppress("unused")
    data class SystemMessage(val content: OneOrMany<SystemContent>, val name: String?) : Message()

    @Serializable
    @SerialName("user")
    @Suppress("unused")
    data class UserMessage(val content: OneOrMany<UserContent>, val name: String? = null) : Message()

    @Serializable
    @SerialName("assistant")
    @Suppress("unused")
    data class AssistantMessage(
        val content: OneOrMany<AssistantContent>,
        val refusal: String? = null,
        val audio: String? = null,
        val name: String? = null,
        @SerialName("tool_calls") val toolCalls: List<ToolCall>
    ) : Message()

    @Serializable
    @SerialName("tool")
    @Suppress("unused")
    data class ToolMessage(
        @SerialName("tool_call_id") val toolCallId: String,
        val content: OneOrMany<ToolResultContent>
    ) : Message()
}