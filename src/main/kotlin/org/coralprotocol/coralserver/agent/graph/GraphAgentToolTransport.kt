@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.util.CORAL_SIGNATURE_HEADER
import org.coralprotocol.coralserver.util.addJsonBodyWithSignature
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


@Serializable
@JsonClassDiscriminator("type")
sealed interface GraphAgentToolTransport : KoinComponent {
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
        private val client by inject<HttpClient>()
        private val config by inject<NetworkConfig>()
        private val json by inject<Json>()

        override suspend fun execute(
            name: String,
            agent: SessionAgent,
            request: CallToolRequest,
        ): CallToolResult {
            try {
                val sessionId = agent.session.id
                val agentName = agent.name

                val urlWithSessionAndAgentPaths = URLBuilder(urlString = url)
                    .appendPathSegments(sessionId, agent.name).buildString()

                agent.logger.info { "Calling custom tool $name, posting to $urlWithSessionAndAgentPaths" }
                val response = client.post(urlWithSessionAndAgentPaths) {
                    contentType(ContentType.Application.Json)
                    addJsonBodyWithSignature(json, config.customToolSecret, request.arguments, signatureHeader)

                    header("X-Coral-Namespace", agent.session.namespace.name)
                    header("X-Coral-SessionId", sessionId)
                    header("X-Coral-AgentName", agentName)
                }

                if (response.status != HttpStatusCode.OK) {
                    agent.logger.warn { "Failed to send custom tool call to $urlWithSessionAndAgentPaths" }
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
                agent.logger.error(e) { "Error executing custom tool $name" }

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