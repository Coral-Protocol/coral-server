package org.coralprotocol.coralserver.orchestrator

import kotlinx.coroutines.*
import org.coralprotocol.coralserver.models.AgentType
import org.coralprotocol.coralserver.models.GraphAgent
import org.coralprotocol.coralserver.models.AgentRuntime

interface Orchestrate {
    fun spawn(): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
}

class Orchestrator(
    val registry: AgentRegistry = AgentRegistry()
) {
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    fun spawn(type: AgentType) {
        spawn(registry.get(type))
    }
    fun spawn(agent: AgentDefinition) {
        handles.add(agent.runtime.spawn())
    }
    fun spawn(runtime: AgentRuntime) {
        handles.add(runtime.spawn())
    }
    fun spawn(type: GraphAgent) {
        when (type) {
           is GraphAgent.Local -> {
               spawn(type.agentType)
           }
            is GraphAgent.Remote -> {
                spawn(type.remote)
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        handles.map { async { it.destroy() } }.awaitAll()
    }
}