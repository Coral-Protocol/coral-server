package org.coralprotocol.coralserver.orchestrator

import kotlinx.coroutines.*
import org.coralprotocol.coralserver.session.GraphAgent
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime

interface Orchestrate {
    fun spawn(agentName: String, connectionUrl: String, options: Map<String, ConfigValue>): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
}

class Orchestrator(
    val registry: AgentRegistry = AgentRegistry()
) {
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    fun spawn(type: AgentType, agentName: String, connectionUrl: String, options: Map<String, ConfigValue>) {
        spawn(registry.get(type), agentName = agentName, connectionUrl = connectionUrl, options = options)
    }

    fun spawn(agent: RegistryAgent, agentName: String, connectionUrl: String, options: Map<String, ConfigValue>) {
        handles.add(agent.runtime.spawn(agentName = agentName, connectionUrl = connectionUrl, options = options))
    }

    fun spawn(runtime: AgentRuntime, agentName: String, connectionUrl: String, options: Map<String, ConfigValue>) {
        handles.add(runtime.spawn(agentName = agentName, connectionUrl = connectionUrl, options = options))
    }

    fun spawn(type: GraphAgent, agentName: String, connectionUrl: String) {
        when (type) {
            is GraphAgent.Local -> {
                spawn(type.agentType, agentName = agentName, connectionUrl = connectionUrl, options = type.options)
            }

            is GraphAgent.Remote -> {
                spawn(type.remote, agentName = agentName, connectionUrl = connectionUrl, options = type.options)
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        handles.map { async { it.destroy() } }.awaitAll()
    }
}