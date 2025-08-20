@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.models.telemetry.generic

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
@SerialName("GenericToolResultContent")
sealed class ToolResultContent {
    @Serializable
    @SerialName("tool_text")
    @Suppress("unused")
    data class Text(val text: String): ToolResultContent()

    @Serializable
    @SerialName("tool_image")
    @Suppress("unused")
    data class Image(
        val data: String,
        val format: ContentFormat? = null,
        val mediaType: ImageMediaType? = null,
        val detail: ImageDetail? = null
    ): ToolResultContent()
}

@Serializable
data class ToolFunction(val name: String, val arguments: String)