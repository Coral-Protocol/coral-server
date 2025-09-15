package org.coralprotocol.coralserver.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.mcp.tools.models.RemoveParticipantInput
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

internal class RemoveParticipantTool: McpTool<RemoveParticipantInput>() {
    override val name: McpToolName
        get() = McpToolName.REMOVE_PARTICIPANT

    override val description: String
        get() = "Remove a participant from a Coral thread"

    override val inputSchema: Tool.Input
        get() = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadId") {
                    put("type", "string")
                    put("description", "ID of the thread")
                }
                putJsonObject("participantId") {
                    put("type", "string")
                    put("description", "ID of the agent to remove")
                }
            },
            required = listOf("threadId", "participantId")
        )

    override val argumentsSerializer: KSerializer<RemoveParticipantInput>
        get() = RemoveParticipantInput.serializer()

    override suspend fun execute(mcpServer: CoralAgentIndividualMcp, arguments: RemoveParticipantInput): McpToolResult {
        return if (mcpServer.localSession.removeParticipantFromThread(
                threadId = arguments.threadId,
                participantId = arguments.participantId
            )) {
            McpToolResult.RemoveParticipantSuccess
        }
        else {
            McpToolResult.Error("Failed to remove participant: Thread not found, participant not found, or thread is closed")
        }
    }
}