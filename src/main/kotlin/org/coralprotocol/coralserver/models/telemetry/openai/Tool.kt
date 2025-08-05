package org.coralprotocol.coralserver.models.telemetry.openai
import kotlinx.serialization.Serializable

@Serializable
enum class ToolType {
    @Suppress("unused")
    Function
}

@Serializable
data class Function(val name: String, val arguments: String)

@Serializable
data class ToolCall(val id: String, val type: ToolType, val function: Function)

@Serializable
enum class ToolResultContentType {
    @Suppress("unused")
    Text
}

@Serializable
data class ToolResultContent(val type: ToolResultContentType, val text: String)