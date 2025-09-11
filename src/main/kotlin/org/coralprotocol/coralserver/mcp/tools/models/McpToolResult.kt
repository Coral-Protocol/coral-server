@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.mcp.tools.models

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.models.ResolvedMessage
import org.coralprotocol.coralserver.models.ResolvedThread
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.models.SessionAgent

@Serializable
@JsonClassDiscriminator("result")
sealed interface McpToolResult {
    @Serializable
    @SerialName("tool_input_error")
    data class ToolInputError(
        val message: String
    ) : McpToolResult

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String
    ) : McpToolResult

    @Serializable
    @SerialName("send_message_success")
    data class SendMessageSuccess(
        val message: ResolvedMessage
    ) : McpToolResult

    @Serializable
    @SerialName("add_participant_success")
    object AddParticipantSuccess : McpToolResult

    @Serializable
    @SerialName("remove_participant_success")
    object RemoveParticipantSuccess : McpToolResult

    @Serializable
    @SerialName("close_thread_success")
    object CloseThreadSuccess : McpToolResult

    @Serializable
    @SerialName("create_thread_success")
    data class CreateThreadSuccess(
        val thread: ResolvedThread
    ) : McpToolResult

    @Serializable
    @SerialName("wait_for_mentions_success")
    data class WaitForMentionsSuccess(
        val messages: List<ResolvedMessage>
    ) : McpToolResult

    @Serializable
    @SerialName("error_timeout")
    object WaitForMentionsTimeout: McpToolResult

    @Serializable
    @SerialName("agent_list_success")
    data class AgentNameList(
        val agents: List<String>
    ) : McpToolResult

    @Serializable
    @SerialName("agent_list_success_with_details")
    data class AgentList(
        val agents: List<SessionAgent>
    ) : McpToolResult

    @Serializable
    @SerialName("close_session_success")
    object CloseSessionSuccess : McpToolResult
}

fun McpToolResult.toCallToolResult(): CallToolResult {
    return CallToolResult(
        content = listOf(TextContent(apiJsonConfig.encodeToString(this)))
    )
}