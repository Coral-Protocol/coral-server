package org.coralprotocol.coralserver.mcp

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import kotlinx.serialization.SerialName

enum class McpTransportType {
    @SerialName("sse")
    SSE,

    @SerialName("streamable_http")
    STREAMABLE_HTTP;

    fun getAbstractTransport(httpClient: HttpClient, url: String) =
        when (this) {
            SSE -> SseClientTransport(httpClient, url)
            STREAMABLE_HTTP -> StreamableHttpClientTransport(httpClient, url)
        }
}
