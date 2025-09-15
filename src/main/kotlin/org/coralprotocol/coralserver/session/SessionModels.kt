@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import com.chrynan.uri.core.UriString
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val logger = KotlinLogging.logger {}

@Serializable
data class CustomTool(
    val transport: ToolTransport,
    val toolSchema: Tool,
)

fun CoralAgentIndividualMcp.addExtraTool(sessionId: String, agentId: String, tool: CustomTool) {
    addTool(
        name = tool.toolSchema.name,
        description = tool.toolSchema.description ?: "",
        inputSchema = tool.toolSchema.inputSchema,
    ) { request ->
        tool.transport.handleRequest(sessionId, agentId, request, tool.toolSchema)
    }
}


@Serializable
@JsonClassDiscriminator("type")
sealed interface ToolTransport {
    @SerialName("http")
    @Serializable
    data class Http(val url: UriString) : ToolTransport {
        override suspend fun handleRequest(
            sessionId: String,
            agentId: String,
            request: CallToolRequest,
            toolSchema: Tool
        ): CallToolResult {
            try {
                val client = HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json()
                    }
                    engine {
                        requestTimeout = 0
                    }
                }

                val response = client.post(url.value) {
                    url {
                        appendPathSegments(sessionId, agentId)
                    }
                    contentType(ContentType.Application.Json)
                    setBody(request.arguments)
                }

                val body = response.bodyAsText()
                return CallToolResult(
                    content = listOf(TextContent(body))
                )
            } catch (ex: Exception) {
                logger.error(ex) { "Error occurred while executing request" }
                return CallToolResult(
                    isError = true,
                    content = listOf(TextContent("Error: $ex"))
                )
            }
        }
    }

    suspend fun handleRequest(
        sessionId: String,
        agentId: String,
        request: CallToolRequest,
        toolSchema: Tool
    ): CallToolResult
}