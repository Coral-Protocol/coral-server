package org.coralprotocol.coralserver.orchestrator

import kotlinx.coroutines.*
import org.coralprotocol.coralserver.session.GraphAgent
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime

interface Orchestrate {
    fun spawn(connectionUrl: String, options: Map<String, ConfigValue>): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
}

class Orchestrator(
    val registry: AgentRegistry = AgentRegistry()
) {
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    fun spawn(type: AgentType, connectionUrl: String, options: Map<String, ConfigValue>) {
        spawn(registry.get(type), connectionUrl, options)
    }

    fun spawn(agent: RegistryAgent, connectionUrl: String, options: Map<String, ConfigValue>) {
        handles.add(agent.runtime.spawn(connectionUrl, options))
    }

    fun spawn(runtime: AgentRuntime, connectionUrl: String, options: Map<String, ConfigValue>) {
        handles.add(runtime.spawn(connectionUrl, options))
    }

    fun spawn(type: GraphAgent, connectionUrl: String) {
        when (type) {
            is GraphAgent.Local -> {
                spawn(type.agentType, connectionUrl, type.options)
            }

            is GraphAgent.Remote -> {
                spawn(type.remote, connectionUrl, type.options)
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        handles.map { async { it.destroy() } }.awaitAll()
    }
}