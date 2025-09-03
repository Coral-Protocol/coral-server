package org.coralprotocol.coralserver.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.models.ListAgentsInput
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

internal class ListAgentsTool: McpTool<ListAgentsInput>() {
    override val name: McpToolName
        get() = McpToolName.LIST_AGENTS

    override val description: String
        get() = "List all the available Coral agents"

    override val inputSchema: Tool.Input
        get() = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("includeDetails") {
                    put("type", "boolean")
                    put("description", "Whether to include agent details in the response")
                }
            },
            required = listOf("includeDetails")
        )

    override val argumentsSerializer: KSerializer<ListAgentsInput>
        get() = ListAgentsInput.serializer()

    override suspend fun execute(mcpServer: CoralAgentIndividualMcp, arguments: ListAgentsInput): McpToolResult {
        val agents = mcpServer.localSession.getAllAgents()

        return if (arguments.includeDetails) {
            McpToolResult.AgentList(agents)
        } else {
            McpToolResult.AgentNameList(agents.map { agent -> agent.id })
        }
    }
}