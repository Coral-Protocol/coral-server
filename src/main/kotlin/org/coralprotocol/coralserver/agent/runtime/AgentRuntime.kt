package org.coralprotocol.coralserver.agent.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

@Serializable
enum class AgentRuntimeTransport {
    @SerialName("sse")
    SSE,

    @SerialName("streamable_http")
    STREAMABLE_HTTP
}

val DEFAULT_AGENT_RUNTIME_TRANSPORT = AgentRuntimeTransport.STREAMABLE_HTTP

interface AgentRuntime {
    val transport: AgentRuntimeTransport

    suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    )
}