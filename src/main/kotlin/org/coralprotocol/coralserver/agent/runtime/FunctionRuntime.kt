package org.coralprotocol.coralserver.agent.runtime

import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

class FunctionRuntime(
    private val function: suspend (
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) -> Unit
) : AgentRuntime() {
    override suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    ) {
        function(executionContext, applicationRuntimeContext)
    }
}