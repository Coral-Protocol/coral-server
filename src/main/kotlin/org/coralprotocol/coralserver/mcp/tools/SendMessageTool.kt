package org.coralprotocol.coralserver.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolName
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.mcp.tools.models.SendMessageInput
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

internal class SendMessageTool: McpTool<SendMessageInput>() {
    override val name: McpToolName
        get() = McpToolName.SEND_MESSAGE

    override val description: String
        get() = "Send a message to a Coral thread"

    override val inputSchema: Tool.Input
        get() = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("threadId") {
                    put("type", "string")
                    put("description", "ID of the thread")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "Content of the message")
                }
                putJsonObject("mentions") {
                    put("type", "array")
                    put("description", "List of agent IDs to mention in the message. You *must* mention an agent for them to be made aware of the message.")
                    putJsonObject("items") {
                        put("type", "string")
                    }
                }
            },
            required = listOf("threadId", "content", "mentions")
        )

    override val argumentsSerializer: KSerializer<SendMessageInput>
        get() = SendMessageInput.serializer()

    override suspend fun execute(mcpServer: CoralAgentIndividualMcp, arguments: SendMessageInput): McpToolResult {

        return McpToolResult.SendMessageSuccess(mcpServer.localSession.sendMessage(
            threadId = arguments.threadId,
            senderId = mcpServer.connectedAgentId,
            content = arguments.content,
            mentions = arguments.mentions
        ).resolve())
    }
}