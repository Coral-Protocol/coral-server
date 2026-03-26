@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime.prototype

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo
import dev.eav.tomlkt.TomlClassDiscriminator
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.koin.core.component.get

@Serializable
data class PrototypeUrlTransformation(val pattern: String, val replacement: PrototypeString)

@Serializable
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface PrototypeToolServerAuth {
    fun resolveClient(executionContext: SessionAgentExecutionContext, client: HttpClient): HttpClient
    fun resolveUrl(executionContext: SessionAgentExecutionContext, url: String): String

    @SerialName("none")
    object None : PrototypeToolServerAuth {
        override fun resolveClient(executionContext: SessionAgentExecutionContext, client: HttpClient): HttpClient =
            client

        override fun resolveUrl(executionContext: SessionAgentExecutionContext, url: String): String = url
    }

    @SerialName("bearer")
    data class Bearer(val token: PrototypeString) : PrototypeToolServerAuth {
        override fun resolveClient(executionContext: SessionAgentExecutionContext, client: HttpClient): HttpClient =
            client.config {
                defaultRequest {
                    headers.append("Authorization", "Bearer ${token.resolve(executionContext)}")
                }
            }

        override fun resolveUrl(executionContext: SessionAgentExecutionContext, url: String): String = url
    }

    @SerialName("url_transformation")
    data class UrlTransformation(val transformations: List<PrototypeUrlTransformation>) : PrototypeToolServerAuth {
        override fun resolveClient(executionContext: SessionAgentExecutionContext, client: HttpClient): HttpClient =
            client

        override fun resolveUrl(executionContext: SessionAgentExecutionContext, url: String): String {
            var resolvedUrl = url
            transformations.forEach {
                resolvedUrl =
                    resolvedUrl.replace(it.pattern, it.replacement.resolve(executionContext))
            }

            return resolvedUrl
        }
    }
}

class McpResolver(
    val url: String,
    val auth: PrototypeToolServerAuth,
    val transport: McpTransportType
) : PrototypeToolServer {
    override suspend fun resolve(executionContext: SessionAgentExecutionContext): List<Tool<*, *>> {
        val httpClient = executionContext.get<HttpClient>()
        val client = Client(
            clientInfo = Implementation(
                name = executionContext.registryAgent.name,
                version = executionContext.registryAgent.version
            )
        )
        client.connect(
            transport.getAbstractTransport(
                auth.resolveClient(executionContext, httpClient),
                auth.resolveUrl(executionContext, url)
            )
        )

        val registry = McpToolRegistryProvider.fromClient(client, McpServerInfo(url = url))
        return registry.tools
    }
}

@Serializable
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface PrototypeToolServer {
    suspend fun resolve(executionContext: SessionAgentExecutionContext): List<Tool<*, *>>

    @Serializable
    @SerialName("mcp_sse")
    data class McpSse(
        val url: String,
        val auth: PrototypeToolServerAuth = PrototypeToolServerAuth.None
    ) : PrototypeToolServer by McpResolver(url, auth, McpTransportType.SSE)

    @Serializable
    @SerialName("mcp_streamable_http")
    data class McpStreamableHttp(
        val url: String,
        val auth: PrototypeToolServerAuth = PrototypeToolServerAuth.None,
    ) : PrototypeToolServer by McpResolver(url, auth, McpTransportType.STREAMABLE_HTTP)
}