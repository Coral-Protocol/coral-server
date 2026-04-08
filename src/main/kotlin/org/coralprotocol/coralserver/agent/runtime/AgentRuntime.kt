package org.coralprotocol.coralserver.agent.runtime

import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

val DEFAULT_AGENT_RUNTIME_TRANSPORT = McpTransportType.STREAMABLE_HTTP

interface AgentRuntime {
    val transport: McpTransportType

    suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    )
}