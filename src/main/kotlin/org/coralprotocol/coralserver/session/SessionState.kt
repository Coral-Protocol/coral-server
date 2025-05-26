package org.coralprotocol.coralserver.session

import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.Thread

/**
 * Immutable representation of session state.
 * All state transitions create new instances rather than modifying existing ones.
 */
data class SessionState(
    val agents: Map<String, Agent> = emptyMap(),
    val threads: Map<String, Thread> = emptyMap(),
    val lastReadIndices: Map<Pair<String, String>, Int> = emptyMap()
) {
    /**
     * Get all threads that an agent participates in.
     */
    fun getThreadsForAgent(agentId: String): List<Thread> {
        return threads.values.filter { it.participants.contains(agentId) }
    }
    
    /**
     * Get a specific thread by ID.
     */
    fun getThread(threadId: String): Thread? {
        return threads[threadId]
    }
    
    /**
     * Get a specific agent by ID.
     */
    fun getAgent(agentId: String): Agent? {
        return agents[agentId]
    }
    
    /**
     * Get all registered agents.
     */
    fun getAllAgents(): List<Agent> {
        return agents.values.toList()
    }
    
    /**
     * Get the last read message index for an agent in a thread.
     */
    fun getLastReadIndex(agentId: String, threadId: String): Int {
        return lastReadIndices[Pair(agentId, threadId)] ?: 0
    }
}