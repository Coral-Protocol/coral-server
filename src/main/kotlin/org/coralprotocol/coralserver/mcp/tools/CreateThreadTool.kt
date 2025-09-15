package org.coralprotocol.coralserver.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.models.CreateThreadInput
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

internal class CreateThreadTool: McpTool<CreateThreadInput>() {
    override val name: McpToolName
        get() = McpToolName.CREATE_THREAD

    override val description: String
        get() = "Create a new Coral thread with a list of participants"

    override val inputSchema: Tool.Input
        get() = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadName") {
                    put("type", "string")
                    put("description", "Name of the thread")
                }
                putJsonObject("participantIds") {
                    put("type", "array")
                    put("description", "List of agent IDs to include as participants")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }
            },
            required = listOf("threadName", "participantIds")
        )

    override val argumentsSerializer: KSerializer<CreateThreadInput>
        get() = CreateThreadInput.serializer()

    override suspend fun execute(mcpServer: CoralAgentIndividualMcp, arguments: CreateThreadInput): McpToolResult {
        return McpToolResult.CreateThreadSuccess(mcpServer.localSession.createThread(
            name = arguments.threadName,
            creatorId = mcpServer.connectedAgentId,
            participantIds = arguments.participantIds
        ).resolve())
    }
}