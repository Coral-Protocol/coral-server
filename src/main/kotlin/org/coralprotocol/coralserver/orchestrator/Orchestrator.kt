package org.coralprotocol.coralserver.orchestrator

import kotlinx.coroutines.*
import org.coralprotocol.coralserver.models.AgentType
import org.coralprotocol.coralserver.models.GraphAgent
import org.coralprotocol.coralserver.models.AgentRuntime

interface Orchestrate {
    fun spawn(options: Map<String, AgentOptionValue>): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
}

class Orchestrator(
    val registry: AgentRegistry = AgentRegistry()
) {
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    fun spawn(type: AgentType, options: Map<String, AgentOptionValue>) {
        spawn(registry.get(type), options)
    }

    fun spawn(agent: AgentDefinition, options: Map<String, AgentOptionValue>) {
        handles.add(agent.runtime.spawn(options))
    }

    fun spawn(runtime: AgentRuntime, options: Map<String, AgentOptionValue>) {
        handles.add(runtime.spawn(options))
    }

    fun spawn(type: GraphAgent) {
        when (type) {
            is GraphAgent.Local -> {
                spawn(type.agentType, type.options)
            }

            is GraphAgent.Remote -> {
                spawn(type.remote, type.options)
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        handles.map { async { it.destroy() } }.awaitAll()
    }
}