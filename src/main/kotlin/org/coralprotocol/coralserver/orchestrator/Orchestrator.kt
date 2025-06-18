package org.coralprotocol.coralserver.orchestrator

import com.chrynan.uri.core.Uri
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.session.GraphAgent
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime

interface Orchestrate {
    fun spawn(
        agentName: String,
        port: UShort, // implementations might misleadingly ignore this port, TODO: Make it less misleading
        relativeMcpServerUri: Uri,
        options: Map<String, ConfigValue>
    ): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
}

class Orchestrator(
    val registry: AgentRegistry = AgentRegistry()
) {
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    fun spawn(type: AgentType, agentName: String, port: UShort, relativeMcpServerUri: Uri, options: Map<String, ConfigValue>) {
        spawn(registry.get(type), agentName = agentName, port = port, relativeMcpServerUri = relativeMcpServerUri, options = options)
    }

    fun spawn(agent: RegistryAgent, agentName: String, port: UShort, relativeMcpServerUri: Uri, options: Map<String, ConfigValue>) {
        handles.add(
            agent.runtime.spawn(
                agentName = agentName,
                port = port,
                relativeMcpServerUri = relativeMcpServerUri,
                options = options
            )
        )
    }

    fun spawn(runtime: AgentRuntime, agentName: String, port: UShort, relativeMcpServerUri: Uri, options: Map<String, ConfigValue>) {
        handles.add(
            runtime.spawn(
                agentName = agentName,
                port = port,
                relativeMcpServerUri = relativeMcpServerUri,
                options = options
            )
        )
    }

    fun spawn(type: GraphAgent, agentName: String, port: UShort, relativeMcpServerUri: Uri) {
        when (type) {
            is GraphAgent.Local -> {
                spawn(
                    type.agentType,
                    agentName = agentName,
                    port = port,
                    relativeMcpServerUri = relativeMcpServerUri,
                    options = type.options
                )
            }

            is GraphAgent.Remote -> {
                spawn(
                    type.remote,
                    agentName = agentName,
                    port = port,
                    relativeMcpServerUri = relativeMcpServerUri,
                    options = type.options
                )
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        handles.map { async { it.destroy() } }.awaitAll()
    }
}