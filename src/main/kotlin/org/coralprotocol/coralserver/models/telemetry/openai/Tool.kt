package org.coralprotocol.coralserver.models.telemetry.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ToolType {
    @SerialName("function")
    @Suppress("unused")
    Function
}

@Serializable
data class Function(val name: String, val arguments: String)

@Serializable
data class ToolCall(val id: String, val type: ToolType, val function: Function)

@Serializable
enum class ToolResultContentType {
    @SerialName("text")
    @Suppress("unused")
    Text
}

@Serializable
@SerialName("OpenAIToolResultContent")
data class ToolResultContent(val type: ToolResultContentType, val text: String)