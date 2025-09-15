package org.coralprotocol.coralserver.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.models.AddParticipantInput
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

internal class AddParticipantTool: McpTool<AddParticipantInput>() {
    override val name: McpToolName
        get() = McpToolName.ADD_PARTICIPANT

    override val description: String
        get() = "Add a participant to a Coral thread"

    override val inputSchema: Tool.Input
        get() = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadId") {
                    put("type", "string")
                    put("description", "ID of the thread")
                }
                putJsonObject("participantId") {
                    put("type", "string")
                    put("description", "ID of the agent to add")
                }
            },
            required = listOf("threadId", "participantId")
        )

    override val argumentsSerializer: KSerializer<AddParticipantInput>
        get() = AddParticipantInput.serializer()

    override suspend fun execute(mcpServer: CoralAgentIndividualMcp, arguments: AddParticipantInput): McpToolResult {
        return if (mcpServer.localSession.addParticipantToThread(
                threadId = arguments.threadId,
                participantId = arguments.participantId
            )) {
            McpToolResult.AddParticipantSuccess
        }
        else {
            McpToolResult.Error("Failed to add participant: Thread not found, participant not found, or thread is closed")
        }
    }
}