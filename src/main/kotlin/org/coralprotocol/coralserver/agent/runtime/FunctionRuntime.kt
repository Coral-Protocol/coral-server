package org.coralprotocol.coralserver.agent.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.mcp.McpTransportType
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

@Serializable
data class FunctionRuntime(
    val volatile: Boolean = false,
    override val transport: McpTransportType = DEFAULT_AGENT_RUNTIME_TRANSPORT,

    @Transient
    val function: suspend (
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) -> Unit = { _, _ -> }
) : AgentRuntime {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) {
        function(executionContext, applicationRuntimeContext)
    }
}