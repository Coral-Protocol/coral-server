package org.coralprotocol.coralserver.mcp

import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import org.coralprotocol.coralserver.mcp.tools.models.McpToolResult
import org.coralprotocol.coralserver.mcp.tools.models.toCallToolResult
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.server.apiJsonConfig

abstract class McpTool<T>() {
    internal abstract val name: McpToolName
    internal abstract val description: String
    internal abstract val inputSchema: Tool.Input
    internal abstract val argumentsSerializer: KSerializer<T>

    protected val logger = KotlinLogging.logger("McpTool.$name")

    internal abstract suspend fun execute(mcpServer: CoralAgentIndividualMcp, arguments: T): McpToolResult

    internal suspend fun executeRaw(mcpServer: CoralAgentIndividualMcp, request: CallToolRequest): CallToolResult {

        val arguments = try {
            apiJsonConfig.decodeFromString(argumentsSerializer, request.arguments.toString())
        }
        catch (e: SerializationException) {
            return McpToolResult.ToolInputError(e.message ?: "Input does not match input for the tool schema").toCallToolResult()
        }

        try {
            return execute(mcpServer, arguments).toCallToolResult()
        }
        catch (e: McpToolException) {
            // Expected error from the tool, likely thrown because of improper input
            return McpToolResult.Error(e.message).toCallToolResult()
        }
        catch (e: Exception) {
            // Anything else is an unknown exception, thrown by some other piece of code.  It should be logged.
            logger.error(e) { "Unexpected error occurred while executing tool" }
            return CallToolResult(
                content = listOf(TextContent(apiJsonConfig.encodeToString(McpToolResult.Error(e.message ?: "Unknown error"))))
            )
        }
    }
}

fun <T> CoralAgentIndividualMcp.addMcpTool(tool: McpTool<T>) {
    addTool(
        name = tool.name.toString(),
        description = tool.description,
        inputSchema = tool.inputSchema,
    ) { request ->
        tool.executeRaw(this, request)
    }
}