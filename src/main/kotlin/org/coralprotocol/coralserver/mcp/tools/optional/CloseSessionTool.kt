package org.coralprotocol.coralserver.mcp.tools.optional

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.models.CloseSessionToolInput
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.session.SessionCloseMode

internal class CloseSessionTool: McpTool<CloseSessionToolInput>() {
    override val name: McpToolName
        get() = McpToolName.CLOSE_SESSION

    override val description: String
        get() = "Closes the Coral session and kills all agents"

    override val inputSchema: Tool.Input
        get() = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("reason") {
                    put("type", "string")
                    put("description", "A description of why the session should be closed")
                }
            },
            required = listOf("reason")
        )

    override val argumentsSerializer: KSerializer<CloseSessionToolInput>
        get() = CloseSessionToolInput.serializer()

    override suspend fun execute(mcpServer: CoralAgentIndividualMcp, arguments: CloseSessionToolInput): McpToolResult {
        mcpServer.coroutineScope.launch {
            // Give a >p(0) chance of the agent actually receiving a response to this tool
            delay(1000)

            mcpServer.localSession.destroy(SessionCloseMode.CLEAN)
        }

        return McpToolResult.CloseSessionSuccess
    }
}