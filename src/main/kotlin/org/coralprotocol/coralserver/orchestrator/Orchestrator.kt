package org.coralprotocol.coralserver.orchestrator

import kotlinx.coroutines.*
import org.coralprotocol.coralserver.session.GraphAgent
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime

interface Orchestrate {
    fun spawn(options: Map<String, ConfigValue>): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
}

class Orchestrator(
    val registry: AgentRegistry = AgentRegistry()
) {
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    fun spawn(type: AgentType, options: Map<String, ConfigValue>) {
        spawn(registry.get(type), options)
    }

    fun spawn(agent: RegistryAgent, options: Map<String, ConfigValue>) {
        handles.add(agent.runtime.spawn(options))
    }

    fun spawn(runtime: AgentRuntime, options: Map<String, ConfigValue>) {
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