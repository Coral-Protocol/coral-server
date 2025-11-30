package org.coralprotocol.coralserver.agent.runtime

import org.coralprotocol.coralserver.session.SessionAgentExecutionContext

abstract class AgentRuntime {
    abstract suspend fun execute(
        executionContext: SessionAgentExecutionContext,
        applicationRuntimeContext: ApplicationRuntimeContext
    )
}