package org.coralprotocol.coralserver.agent.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.coralprotocol.coralserver.EventBus

class FunctionRuntime(
    private val function: suspend (params: RuntimeParams) -> Unit
) : Orchestrate {
    override fun spawn(
        params: RuntimeParams,
        eventBus: EventBus<RuntimeEvent>,
        applicationRuntimeContext: ApplicationRuntimeContext
    ): OrchestratorHandle {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            function(params)
        }

        return object : OrchestratorHandle {
            override suspend fun destroy() {
                scope.cancel()
            }
        }
    }
}