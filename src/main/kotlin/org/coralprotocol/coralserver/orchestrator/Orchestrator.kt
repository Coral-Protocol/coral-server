package org.coralprotocol.coralserver.orchestrator

import kotlinx.coroutines.*
import org.coralprotocol.coralserver.models.AgentType
import kotlin.coroutines.coroutineContext

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
    suspend fun spawn(type: AgentType) {
        val provider = registry.get(type)
        handles.add(provider.spawn())
    }

    suspend fun destroy(): Unit = coroutineScope {
        handles.map { async { it.destroy() } }.awaitAll()
    }
}