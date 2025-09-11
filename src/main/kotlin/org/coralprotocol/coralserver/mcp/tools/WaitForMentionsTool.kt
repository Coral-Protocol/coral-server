package org.coralprotocol.coralserver.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.mcp.tools.models.WaitForMentionsInput
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.session.models.SessionAgentState

internal class WaitForMentionsTool: McpTool<WaitForMentionsInput>() {
    private val maxWaitForMentionsTimeoutMs = 1000 * 60 * 10

    override val name: McpToolName
        get() = McpToolName.WAIT_FOR_MENTIONS

    override val description: String
        get() = "Wait until mentioned in all Coral threads. Call this tool when you're done or want to wait for another agent to respond. This will block until a message is received. You will see all unread messages."

    override val inputSchema: Tool.Input
        get() = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("timeoutMs") {
                    put("type", "number")
                    put("description", "Timeout in milliseconds (default: $maxWaitForMentionsTimeoutMs ms). Must be between 0 and $maxWaitForMentionsTimeoutMs ms.")
                }
            },
            required = listOf("timeoutMs")
        )

    override val argumentsSerializer: KSerializer<WaitForMentionsInput>
        get() = WaitForMentionsInput.serializer()

    override suspend fun execute(mcpServer: CoralAgentIndividualMcp, arguments: WaitForMentionsInput): McpToolResult {
        try {
            logger.info { "Waiting for mentions for agent ${mcpServer.connectedAgentId} with timeout ${arguments.timeoutMs}ms" }

            if (arguments.timeoutMs < 0)
                return McpToolResult.Error("Timeout must be greater than 0")

            if (arguments.timeoutMs > maxWaitForMentionsTimeoutMs)
                return McpToolResult.Error("Timeout must not exceed the maximum of $maxWaitForMentionsTimeoutMs ms")

            mcpServer.localSession.setAgentState(agentId = mcpServer.connectedAgentId, state = SessionAgentState.Listening)

            val messages = mcpServer.localSession.waitForMentions(
                agentId = mcpServer.connectedAgentId,
                timeoutMs = arguments.timeoutMs
            )

            mcpServer.localSession.setAgentState(agentId = mcpServer.connectedAgentId, state = SessionAgentState.Busy)

            if (messages.isNotEmpty()) {
                logger.info { "Received ${messages.size} messages for agent ${mcpServer.connectedAgentId}" }
                return McpToolResult.WaitForMentionsSuccess(messages.map { it.resolve() })
            }
            else {
                return McpToolResult.WaitForMentionsTimeout
            }
        }
        finally {
            mcpServer.localSession.setAgentState(agentId = mcpServer.connectedAgentId, state = SessionAgentState.Busy)
        }
    }
}