@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.runtime.prototype

import dev.eav.tomlkt.TomlClassDiscriminator
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
enum class PrototypeMcpServerType {
    @SerialName("mcp_streamable_http")
    MCP_STREAMABLE_HTTP,

    @SerialName("mcp_sse")
    MCP_SSE
}

@Serializable
data class PrototypeUrlTransformation(val pattern: PrototypeString, val replacement: PrototypeString)

@Serializable
@JsonClassDiscriminator("type")
@TomlClassDiscriminator("type")
sealed interface PrototypeMcpServerAuth {
    @SerialName("none")
    object None : PrototypeMcpServerAuth

    @SerialName("bearer")
    data class Bearer(val token: PrototypeString) : PrototypeMcpServerAuth

    @SerialName("url_transformation")
    data class UrlTransformation(val transformations: List<PrototypeUrlTransformation>) : PrototypeMcpServerAuth
}
/// https://firecrawl-websearch-mcp-server.klavis.ai/mcp/?instance_id=8f2a68c7-816c-4ba2-885b-f401545972d9

@Serializable
data class PrototypeMcpServer(
    val url: PrototypeString,
    val type: PrototypeMcpServerType = PrototypeMcpServerType.MCP_STREAMABLE_HTTP,
    val auth: PrototypeMcpServerAuth = PrototypeMcpServerAuth.None
)
