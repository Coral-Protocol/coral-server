package org.coralprotocol.coralserver.orchestrator

import com.chrynan.uri.core.Uri
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.session.GraphAgent
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime
import org.coralprotocol.coralserver.orchestrator.runtime.RuntimeParams

interface Orchestrate {
    fun spawn(
        params: RuntimeParams,
    ): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
}

class Orchestrator(
    val app: AppConfigLoader = AppConfigLoader(null)
) {
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    fun spawn(type: AgentType, params: RuntimeParams) {
        val agent = app.config.registry?.get(type) ?: return;
        spawn(agent, params)
    }

    fun spawn(agent: RegistryAgent, params: RuntimeParams) {
        handles.add(
            agent.runtime.spawn(params)
        )
    }

    fun spawn(runtime: AgentRuntime, params: RuntimeParams) {
        handles.add(
            runtime.spawn(params)
        )
    }

    fun spawn(type: GraphAgent, agentName: String, port: UShort, relativeMcpServerUri: Uri) {
        val params = RuntimeParams(
            agentName = agentName,
            mcpServerPort = port,
            mcpServerRelativeUri = relativeMcpServerUri,
            systemPrompt = type.systemPrompt,
            options = type.options
        )
        when (type) {
            is GraphAgent.Local -> {
                spawn(
                    type.agentType,
                    params
                )
            }

            is GraphAgent.Remote -> {
                spawn(
                    type.remote,
                    params,
                )
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        handles.map { async { it.destroy() } }.awaitAll()
    }
}