@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.generic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
enum class ContentFormat {
    @SerialName("base64")
    @Suppress("unused")
    BASE64,

    @SerialName("string")
    @Suppress("unused")
    STRING
}

@Serializable
enum class ImageDetail {
    @SerialName("low")
    @Suppress("unused")
    LOW,

    @SerialName("high")
    @Suppress("unused")
    HIGH,

    @SerialName("auto")
    @Suppress("unused")
    AUTO
}

@Serializable
enum class ImageMediaType {
    @SerialName("jpeg")
    @Suppress("unused")
    JPEG,

    @SerialName("png")
    @Suppress("unused")
    PNG,

    @SerialName("gif")
    @Suppress("unused")
    GIF,

    @SerialName("webp")
    @Suppress("unused")
    WEBP,

    @SerialName("heic")
    @Suppress("unused")
    HEIC,

    @SerialName("heif")
    @Suppress("unused")
    HEIF,

    @SerialName("svg")
    @Suppress("unused")
    SVG,
}

@Serializable
enum class DocumentMediaType {
    @SerialName("pdf")
    @Suppress("unused")
    PDF,

    @SerialName("txt")
    @Suppress("unused")
    TXT,

    @SerialName("rtf")
    @Suppress("unused")
    RTF,

    @SerialName("html")
    @Suppress("unused")
    HTML,

    @SerialName("css")
    @Suppress("unused")
    CSS,

    @SerialName("markdown")
    @Suppress("unused")
    MARKDOWN,

    @SerialName("csv")
    @Suppress("unused")
    CSV,

    @SerialName("xml")
    @Suppress("unused")
    XML,

    @SerialName("javascript")
    @Suppress("unused")
    JAVASCRIPT,

    @SerialName("python")
    @Suppress("unused")
    PYTHON,
}

@Serializable
enum class AudioMediaType {
    @SerialName("wav")
    @Suppress("unused")
    WAV,

    @SerialName("mp3")
    @Suppress("unused")
    MP3,

    @SerialName("aiff")
    @Suppress("unused")
    AIFF,

    @SerialName("aac")
    @Suppress("unused")
    AAC,

    @SerialName("ogg")
    @Suppress("unused")
    OGG,

    @SerialName("flac")
    @Suppress("unused")
    FLAC,
}

@Serializable
enum class VideoMediaType {
    @SerialName("avi")
    @Suppress("unused")
    AVI,

    @SerialName("mp4")
    @Suppress("unused")
    MP4,

    @SerialName("mpeg")
    @Suppress("unused")
    MPEG,
}

@Serializable
@JsonClassDiscriminator("type")
@SerialName("GenericUserContent")
sealed class UserContent {
    @Serializable
    @SerialName("text")
    @Suppress("unused")
    data class Text(val text: String): UserContent()

    @Serializable
    @SerialName("tool_result")
    @Suppress("unused")
    data class ToolResult(
        val id: String,
        val callId: String? = null,
        val content: List<ToolResultContent>
    ): UserContent()

    @Serializable
    @SerialName("image")
    @Suppress("unused")
    data class Image(
        val data: String,
        val format: ContentFormat? = null,
        val mediaType: ImageMediaType? = null,
        val detail: ImageDetail? = null
    ): UserContent()

    @Serializable
    @SerialName("audio")
    @Suppress("unused")
    data class Audio(
        val data: String,
        val format: ContentFormat? = null,
        val mediaType: AudioMediaType? = null,
    ): UserContent()

    @Serializable
    @SerialName("video")
    @Suppress("unused")
    @JsonIgnoreUnknownKeys
    data class Video(
        val data: String,
        val format: ContentFormat? = null,
        val mediaType: VideoMediaType? = null,
    ): UserContent()

    @Serializable
    @SerialName("document")
    @Suppress("unused")
    data class Document(
        val data: String,
        val format: ContentFormat? = null,
        val mediaType: DocumentMediaType? = null,
    ): UserContent()
}

@Serializable
@JsonClassDiscriminator("type")
@SerialName("GenericAssistantContent")
sealed class AssistantContent {
    @Serializable
    @SerialName("assistant_text")
    @Suppress("unused")
    data class Text(val text: String) : AssistantContent()

    @Serializable
    @SerialName("assistant_tool_call")
    @Suppress("unused")
    data class ToolCall(
        val id: String,
        val callId: String? = null,
        val function: ToolFunction
    ) : AssistantContent()

    @Serializable
    @SerialName("assistant_reasoning")
    @Suppress("unused")
    data class Reasoning(val reasoning: List<String>) : AssistantContent()
}
