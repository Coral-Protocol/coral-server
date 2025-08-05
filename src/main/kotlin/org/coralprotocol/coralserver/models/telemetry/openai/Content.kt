@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.openai

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.models.telemetry.generic.AudioMediaType
import org.coralprotocol.coralserver.models.telemetry.generic.ImageDetail

@Serializable
enum class SystemContentType {
    @Suppress("unused")
    TEXT,
}

@Serializable
data class ImageUrl(val url: String, val detail: ImageDetail)

@Serializable
data class InputAudio(val data: String, val format: AudioMediaType)

@Serializable
data class SystemContent(val type: SystemContentType, val text: String)

@Serializable
@JsonClassDiscriminator("type")
sealed class UserContent {
    @Serializable
    @Suppress("unused")
    data class Text(val text: String): UserContent()

    @Serializable
    @Suppress("unused")
    data class Image(val imageUrl: ImageUrl): UserContent()

    @Serializable
    @Suppress("unused")
    data class Audio(val inputAudio: InputAudio): UserContent()
}

@Serializable
@JsonClassDiscriminator("type")
sealed class AssistantContent {
    @Serializable
    @Suppress("unused")
    data class Text(val text: String) : AssistantContent()

    @Serializable
    @Suppress("unused")
    data class Refusal(val refusal: String) : AssistantContent()
}