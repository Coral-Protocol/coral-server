package org.coralprotocol.coralserver.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.client.Client
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import org.coralprotocol.coralserver.session.SessionAgent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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
) : KoinComponent {
    private val json by inject<Json>()

    suspend fun execute(agent: SessionAgent, encodedArguments: JsonObject): CallToolResult {
        val arguments = try {
            json.decodeFromJsonElement(inputSerializer, encodedArguments)
        } catch (e: SerializationException) {
            agent.logger.error(e) { "Couldn't deserialize input given to $name" }

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
            val jsonObj = json.encodeToJsonElement(outputSerializer, out)

            CallToolResult(
                content = listOf(TextContent(jsonObj.toString())),
                structuredContent = jsonObj as? JsonObject,
                isError = false
            )
        } catch (e: McpToolException) {
            CallToolResult(
                content = listOf(TextContent(e.message)),
                structuredContent = buildJsonObject {
                    put("error", e.message)
                },
                isError = true,
            )
        } catch (e: Exception) {
            agent.logger.error(e) { "Unexpected error occurred while executing tool $name" }

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
        val jsonObj = json.encodeToJsonElement(inputSerializer, arguments) as JsonObject

        val response =
            client.callTool(CallToolRequest(name.toString(), jsonObj))
                ?: throw McpToolException("No response from server")

        if (response.isError == true) {
            val errorMsg = response.structuredContent?.get("error")?.jsonPrimitive?.content ?: "Unknown error"
            throw McpToolException(errorMsg)
        } else {
            val structured = response.structuredContent
                ?: throw McpToolException("Response missing expected structured content")

            return json.decodeFromJsonElement(outputSerializer, structured)
                ?: throw McpToolException("Response did not match expected output type")
        }
    }
}