@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.generic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class ContentFormat {
    @Suppress("unused")
    BASE64,

    @Suppress("unused")
    STRING
}

@Serializable
enum class ImageDetail {
    @Suppress("unused")
    LOW,

    @Suppress("unused")
    HIGH,

    @Suppress("unused")
    AUTO
}

enum class ImageMediaType {
    @Suppress("unused")
    JPEG,

    @Suppress("unused")
    PNG,

    @Suppress("unused")
    GIF,

    @Suppress("unused")
    WEBP,

    @Suppress("unused")
    HEIC,

    @Suppress("unused")
    HEIF,

    @Suppress("unused")
    SVG,
}

enum class DocumentMediaType {
    @Suppress("unused")
    PDF,

    @Suppress("unused")
    TXT,

    @Suppress("unused")
    RTF,

    @Suppress("unused")
    HTML,

    @Suppress("unused")
    CSS,

    @Suppress("unused")
    MARKDOWN,

    @Suppress("unused")
    CSV,

    @Suppress("unused")
    XML,

    @Suppress("unused")
    JAVASCRIPT,

    @Suppress("unused")
    PYTHON,
}

@Serializable
enum class AudioMediaType {
    @Suppress("unused")
    WAV,

    @Suppress("unused")
    MP3,

    @Suppress("unused")
    AIFF,

    @Suppress("unused")
    AAC,

    @Suppress("unused")
    OGG,

    @Suppress("unused")
    FLAC,
}

@Serializable
@JsonClassDiscriminator("type")
sealed class UserContent {
    @Serializable
    @Suppress("unused")
    data class Text(val text: String): UserContent()

    @Serializable
    @Suppress("unused")
    data class ToolResult(
        val id: String,
        val callId: String? = null,
        val content: ToolResultContent
    ): UserContent()

    @Serializable
    @Suppress("unused")
    data class Image(
        val data: String,
        val format: ContentFormat? = null,
        val mediaType: ImageMediaType? = null,
        val detail: ImageDetail? = null
    ): UserContent()

    @Serializable
    @Suppress("unused")
    data class Audio(
        val data: String,
        val format: ContentFormat? = null,
        val mediaType: AudioMediaType? = null,
    ): UserContent()

    @Serializable
    @Suppress("unused")
    data class Document(
        val data: String,
        val format: ContentFormat? = null,
        val mediaType: DocumentMediaType? = null,
    ): UserContent()
}

@Serializable
@JsonClassDiscriminator("type")
sealed class AssistantContent {
    @Serializable
    @Suppress("unused")
    data class Text(val text: String) : AssistantContent()

    @Serializable
    @Suppress("unused")
    data class ToolCall(
        val id: String,
        val callId: String? = null,
        val function: ToolFunction
    ) : AssistantContent()

    @Serializable
    @Suppress("unused")
    data class Reasoning(val reasoning: String) : AssistantContent()
}
