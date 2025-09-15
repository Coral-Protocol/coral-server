package org.coralprotocol.coralserver.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.models.CloseThreadInput
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

internal class CloseThreadTool: McpTool<CloseThreadInput>() {
    override val name: McpToolName
        get() = McpToolName.CLOSE_THREAD

    override val description: String
        get() = "Closes a Coral thread with a summary"

    override val inputSchema: Tool.Input
        get() = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadId") {
                    put("type", "string")
                    put("description", "ID of the thread to close")
                }
                putJsonObject("summary") {
                    put("type", "string")
                    put("description", "Summary of the thread")
                }
            },
            required = listOf("threadId", "summary")
        )

    override val argumentsSerializer: KSerializer<CloseThreadInput>
        get() = CloseThreadInput.serializer()

    override suspend fun execute(mcpServer: CoralAgentIndividualMcp, arguments: CloseThreadInput): McpToolResult {
        return if (mcpServer.localSession.closeThread(
                threadId = arguments.threadId,
                summary = arguments.summary
            )) {
            McpToolResult.CloseThreadSuccess
        }
        else {
            McpToolResult.Error("Failed to close thread: Thread not found")
        }
    }
}