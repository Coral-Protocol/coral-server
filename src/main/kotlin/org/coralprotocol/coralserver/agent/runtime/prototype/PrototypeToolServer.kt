@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime.prototype

import ai.koog.agents.core.tools.Tool
import dev.eav.tomlkt.TomlClassDiscriminator
import io.ktor.client.*
import io.ktor.client.plugins.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

@Serializable
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface PrototypeToolServerAuth {
    fun resolveClient(executionContext: SessionAgentExecutionContext, client: HttpClient): HttpClient

    @Serializable
    @SerialName("none")
    object None : PrototypeToolServerAuth {
        override fun resolveClient(executionContext: SessionAgentExecutionContext, client: HttpClient): HttpClient =
            client
    }

    @Serializable
    @SerialName("authorization_header")
    data class AuthorizationHeader(
        @SerialName("header")
        val authorizationHeader: PrototypeString
    ) : PrototypeToolServerAuth {
        override fun resolveClient(executionContext: SessionAgentExecutionContext, client: HttpClient): HttpClient =
            client.config {
                defaultRequest {
                    headers.append("Authorization", authorizationHeader.resolve(executionContext))
                }
            }
    }

    @Serializable
    @SerialName("bearer")
    data class Bearer(val token: PrototypeString) : PrototypeToolServerAuth {
        override fun resolveClient(executionContext: SessionAgentExecutionContext, client: HttpClient): HttpClient =
            client.config {
                defaultRequest {
                    headers.append("Authorization", "Bearer ${token.resolve(executionContext)}")
                }
            }
    }
}

interface ResolvedPrototypeToolServer {
    val resolvedTools: List<Tool<*, *>>
    suspend fun close()
}

@Serializable
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface PrototypeToolServer {
    suspend fun resolve(executionContext: SessionAgentExecutionContext): ResolvedPrototypeToolServer

    @Serializable
    @SerialName("mcp_sse")
    data class McpSse(
        val url: PrototypeString,
        val auth: PrototypeToolServerAuth = PrototypeToolServerAuth.None
    ) : PrototypeToolServer by McpResolver(url, auth, McpTransportType.SSE)

    @Serializable
    @SerialName("mcp_streamable_http")
    data class McpStreamableHttp(
        val url: PrototypeString,
        val auth: PrototypeToolServerAuth = PrototypeToolServerAuth.None,
    ) : PrototypeToolServer by McpResolver(url, auth, McpTransportType.STREAMABLE_HTTP)
}