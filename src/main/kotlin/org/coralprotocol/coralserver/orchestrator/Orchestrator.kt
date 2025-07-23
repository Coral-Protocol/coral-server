package org.coralprotocol.coralserver.orchestrator

import com.chrynan.uri.core.Uri
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.config.AppConfigLoader
import org.coralprotocol.coralserver.session.GraphAgent
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime
import org.coralprotocol.coralserver.orchestrator.runtime.RuntimeParams
import org.coralprotocol.coralserver.session.SessionManager

enum class LogKind {
    STDOUT,
    STDERR,
}

@Serializable
sealed interface RuntimeEvent {
    @Serializable
    data class Log(val timestamp: Long = System.currentTimeMillis(), val kind: LogKind, val message: String) :
        RuntimeEvent
    @Serializable
    data class Stopped(val timestamp: Long = System.currentTimeMillis()): RuntimeEvent
}

interface Orchestrate {
    fun spawn(
        params: RuntimeParams,
        eventBus: EventBus<RuntimeEvent>,
        sessionManager: SessionManager?,
    ): OrchestratorHandle
}

interface OrchestratorHandle {
    suspend fun destroy()
}

class Orchestrator(
    val app: AppConfigLoader = AppConfigLoader(null),
) {
    private val eventBusses: MutableMap<String, MutableMap<String, EventBus<RuntimeEvent>>> = mutableMapOf()
    private val handles: MutableList<OrchestratorHandle> = mutableListOf()


    fun getBus(sessionId: String, agentId: String): EventBus<RuntimeEvent>? = eventBusses[sessionId]?.get(agentId)

    private fun getBusOrCreate(sessionId: String, agentId: String) = eventBusses.getOrPut(sessionId) {
        mutableMapOf()
    }.getOrPut(agentId) {
        EventBus(replay = 512)
    }

    fun spawn(type: AgentType, params: RuntimeParams, sessionManager: SessionManager?) {
        val agent = app.config.registry?.get(type) ?: return;
        spawn(agent, params, sessionManager = sessionManager)
    }

    fun spawn(agent: RegistryAgent, params: RuntimeParams, sessionManager: SessionManager?) {
        val bus = getBusOrCreate(params.sessionId, params.agentName)
        handles.add(
            agent.runtime.spawn(params, bus, sessionManager)
        )
    }

    fun spawn(runtime: AgentRuntime, params: RuntimeParams, sessionManager: SessionManager?) {
        val bus = getBusOrCreate(params.sessionId, params.agentName)
        handles.add(
            runtime.spawn(params, bus, sessionManager)
        )
    }

    fun spawn(sessionId: String, type: GraphAgent, agentName: String, port: UShort, relativeMcpServerUri: Uri, sessionManager: SessionManager?) {
        val params = RuntimeParams(
            sessionId = sessionId,
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
                    params,
                    sessionManager = sessionManager
                )
            }

            is GraphAgent.Remote -> {
                spawn(
                    type.remote,
                    params,
                    sessionManager = sessionManager
                )
            }
        }
    }

    suspend fun destroy(): Unit = coroutineScope {
        handles.map { async { it.destroy() } }.awaitAll()
    }
}