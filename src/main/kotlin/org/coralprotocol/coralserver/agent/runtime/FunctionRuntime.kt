package org.coralprotocol.coralserver.agent.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

@Serializable
class FunctionRuntime(
    @Transient
    private val function: suspend (
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) -> Unit = { _, _ ->

    }
) : AgentRuntime() {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) {
        function(executionContext, applicationRuntimeContext)
    }
}