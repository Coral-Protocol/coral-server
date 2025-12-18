@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.util.CORAL_SIGNATURE_HEADER
import org.coralprotocol.coralserver.util.addJsonBodyWithSignature

@Serializable
@JsonClassDiscriminator("type")
sealed interface GraphAgentToolTransport {
    suspend fun execute(
        name: String,
        agent: SessionAgent,
        request: CallToolRequest,
    ): CallToolResult

    @SerialName("http")
    @Serializable
    data class Http(
        val url: String,

        @Optional
        val signatureHeader: String = CORAL_SIGNATURE_HEADER,
    ) : GraphAgentToolTransport {
        override suspend fun execute(
            name: String,
            agent: SessionAgent,
            request: CallToolRequest,
        ): CallToolResult {
            try {
                val response = agent.httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    addJsonBodyWithSignature(agent.customToolSecret, request.arguments, signatureHeader)

                    header("X-Coral-Namespace", agent.session.namespace.name)
                    header("X-Coral-SessionId", agent.session.id)
                    header("X-Coral-AgentName", agent.name)
                }

                if (response.status != HttpStatusCode.OK) {
                    return CallToolResult(
                        isError = true,
                        content = listOf(TextContent("Error code ${response.status.value} returned"))
                    )
                }

                val body = response.bodyAsText()
                return CallToolResult(
                    content = listOf(TextContent(body))
                )
            } catch (e: Exception) {
                agent.logger.error("Error executing custom tool $name", e)

                return CallToolResult(
                    isError = true,

                    // best not to leak the exception to the agent.  non-200 statuses are reported without this catch
                    // block
                    content = listOf(TextContent("Unknown error"))
                )
            }
        }
    }
}