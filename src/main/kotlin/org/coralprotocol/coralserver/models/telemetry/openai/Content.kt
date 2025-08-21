@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.openai

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.models.telemetry.generic.AudioMediaType
import org.coralprotocol.coralserver.models.telemetry.generic.ImageDetail

@Serializable
enum class SystemContentType {
    @SerialName("text")
    @Suppress("unused")
    TEXT,
}

@Serializable
data class ImageUrl(val url: String, val detail: ImageDetail)

@Serializable
data class InputAudio(val data: String, val format: AudioMediaType)

@Serializable
@SerialName("OpenAISystemContent")
data class SystemContent(val type: SystemContentType, val text: String)

@Serializable
data class AudioAssistant(val id: String);

@Serializable
@JsonClassDiscriminator("type")
@SerialName("OpenAIUserContent")
sealed class UserContent {
    @Serializable
    @SerialName("text")
    @Suppress("unused")
    data class Text(val text: String): UserContent()

    @Serializable
    @SerialName("image_url")
    @Suppress("unused")
    data class Image(val imageUrl: ImageUrl): UserContent()

    @Serializable
    @SerialName("audio")
    @Suppress("unused")
    data class Audio(val inputAudio: InputAudio): UserContent()
}

@Serializable
@JsonClassDiscriminator("type")
@SerialName("OpenAIAssistantContent")
sealed class AssistantContent {
    @Serializable
    @SerialName("text")
    @Suppress("unused")
    data class Text(val text: String) : AssistantContent()

    @Serializable
    @SerialName("refusal")
    @Suppress("unused")
    data class Refusal(val refusal: String) : AssistantContent()
}