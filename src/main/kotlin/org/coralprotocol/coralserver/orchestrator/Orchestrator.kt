package org.coralprotocol.coralserver.orchestrator

import com.chrynan.uri.core.Uri
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.session.GraphAgent
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime

interface Orchestrate {
    fun spawn(
        agentName: String,
        port: UShort, // implementations might misleadingly ignore this port, TODO: Make it less misleading
        relativeMcpServerUri: Uri,
        options: Map<String, ConfigValue>,
        sessionId: String
    ): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
    var sessionId: String
}

class Orchestrator(
    val app: AppConfigLoader = AppConfigLoader(null)
) {
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()

    fun spawn(type: AgentType, agentName: String, port: UShort, relativeMcpServerUri: Uri, options: Map<String, ConfigValue>, sessionId: String) {
        val agent = app.config.registry?.get(type) ?: return;
        spawn(agent, agentName = agentName, port = port, relativeMcpServerUri = relativeMcpServerUri, options = options, sessionId = sessionId)
    }

    fun spawn(agent: RegistryAgent, agentName: String, port: UShort, relativeMcpServerUri: Uri, options: Map<String, ConfigValue>, sessionId: String) {
        handles.add(
            agent.runtime.spawn(
                agentName = agentName,
                port = port,
                relativeMcpServerUri = relativeMcpServerUri,
                options = options,
                sessionId = sessionId
            )
        )
    }

    fun spawn(runtime: AgentRuntime, agentName: String, port: UShort, relativeMcpServerUri: Uri, options: Map<String, ConfigValue>, sessionId: String) {
        handles.add(
            runtime.spawn(
                agentName = agentName,
                port = port,
                relativeMcpServerUri = relativeMcpServerUri,
                options = options,
                sessionId = sessionId
            )
        )
    }

    fun spawn(type: GraphAgent, agentName: String, port: UShort, relativeMcpServerUri: Uri, sessionId: String) {
        when (type) {
            is GraphAgent.Local -> {
                spawn(
                    type.agentType,
                    agentName = agentName,
                    port = port,
                    relativeMcpServerUri = relativeMcpServerUri,
                    options = type.options,
                    sessionId = sessionId
                )
            }

            is GraphAgent.Remote -> {
                spawn(
                    type.remote,
                    agentName = agentName,
                    port = port,
                    relativeMcpServerUri = relativeMcpServerUri,
                    options = type.options,
                    sessionId = sessionId
                )
            }
        }
    }

    suspend fun destroy(sessionId: String): Unit = coroutineScope {
        handles.filter { it.sessionId == sessionId }
            .map { async { it.destroy() } }
            .awaitAll()
        handles.removeIf { it.sessionId == sessionId }
    }
    suspend fun destroyAll(): Unit = coroutineScope {
        handles.map { async { it.destroy() } }.awaitAll()
    }
}