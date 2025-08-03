@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.generic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@Serializable
@JsonClassDiscriminator("type")
sealed class ToolResultContent {
    @Serializable
    @SerialName("text")
    @Suppress("unused")
    data class Text(val text: String): ToolResultContent()

    @Serializable
    @SerialName("tool_result")
    @Suppress("unused")
    data class Image(
        val data: String,
        val format: ContentFormat? = null,
        @SerialName("media_type") val mediaType: ImageMediaType? = null,
        val detail: ImageDetail? = null
    ): ToolResultContent()
}

@Serializable
data class ToolFunction(val name: String, val arguments: JsonElement)