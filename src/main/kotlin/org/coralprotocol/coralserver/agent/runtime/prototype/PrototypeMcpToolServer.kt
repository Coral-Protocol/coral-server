package org.coralprotocol.coralserver.agent.runtime.prototype

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo
import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext
import org.koin.core.component.get

class McpResolver(
    val url: PrototypeString,
    val auth: PrototypeToolServerAuth,
    val transport: McpTransportType
) : PrototypeToolServer {
    override suspend fun resolve(executionContext: SessionAgentExecutionContext): ResolvedMcpToolServer {
        val httpClient = executionContext.get<HttpClient>()
        val url = url.resolve(executionContext)
        val client = Client(
            clientInfo = Implementation(
                name = executionContext.registryAgent.name,
                version = executionContext.registryAgent.version
            )
        )
        client.connect(
            transport.getAbstractTransport(
                auth.resolveClient(executionContext, httpClient),
                url
            )
        )

        val registry = McpToolRegistryProvider.fromClient(client, McpServerInfo(url = url))
        return ResolvedMcpToolServer(registry.tools, client)
    }
}

class ResolvedMcpToolServer(
    override val resolvedTools: List<Tool<*, *>>,
    private val client: Client,
) : ResolvedPrototypeToolServer {
    override suspend fun close() {
        client.close()
    }
}