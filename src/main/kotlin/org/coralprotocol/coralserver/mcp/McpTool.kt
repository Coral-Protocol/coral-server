package org.coralprotocol.coralserver.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.SessionAgent

@Serializable
data class GenericSuccessOutput(val message: String)

class McpTool<In, Out>(
    val name: McpToolName,
    val description: String,
    val requiredSnippets: Set<McpInstructionSnippet>,
    val inputSchema: Tool.Input,
    private val executor: suspend (agent: SessionAgent, arguments: In) -> Out,
    private val inputSerializer: KSerializer<In>,
    private val outputSerializer: KSerializer<Out>,
) {
    suspend fun execute(agent: SessionAgent, encodedArguments: JsonObject): CallToolResult {
        val arguments = try {
            apiJsonConfig.decodeFromJsonElement(inputSerializer, encodedArguments)
        }
        catch (e: SerializationException) {
            agent.logger.error("Couldn't deserialize input given to $name", e)

            return CallToolResult(
                content = listOf(TextContent(e.message)),
                structuredContent = buildJsonObject {
                    put("error", e.message)
                },
                isError = true,
            )
        }

        val out = executor(agent, arguments)
        return try {
            val json = apiJsonConfig.encodeToJsonElement(outputSerializer, out)

            CallToolResult(
                content = listOf(TextContent(json.toString())),
                structuredContent = json as? JsonObject,
                isError = false
            )
        }
        catch (e: McpToolException) {
            CallToolResult(
                content = listOf(TextContent(e.message)),
                structuredContent = buildJsonObject {
                    put("error", e.message)
                },
                isError = true,
            )
        }
        catch (e: Exception) {
            agent.logger.error("Unexpected error occurred while executing tool $name", e)

            CallToolResult(
                content = listOf(TextContent(e.message)),
                structuredContent = buildJsonObject {
                    put("error", e.message)
                },
                isError = true,
            )
        }
    }

    suspend fun executeOn(client: Client, arguments: In): Out {
        val json = apiJsonConfig.encodeToJsonElement(inputSerializer, arguments) as JsonObject

        val response =
            client.callTool(CallToolRequest(name.toString(), json)) ?: throw McpToolException("No response from server")

        if (response.isError == true) {
            val errorMsg = response.structuredContent?.get("error")?.jsonPrimitive?.content ?: "Unknown error"
            throw McpToolException(errorMsg)
        }
        else {
            val structured = response.structuredContent
                ?: throw McpToolException("Response missing expected structured content")

            return apiJsonConfig.decodeFromJsonElement(outputSerializer, structured)
                ?: throw McpToolException("Response did not match expected output type")
        }
    }
}