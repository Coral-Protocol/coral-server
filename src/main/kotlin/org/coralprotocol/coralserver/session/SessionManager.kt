package org.coralprotocol.coralserver.session

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.coralprotocol.coralserver.orchestrator.Orchestrator
import java.util.concurrent.ConcurrentHashMap

fun AgentGraph.adjacencyMap(): Map<String, Set<String>> {
    val map = mutableMapOf<String, MutableSet<String>>()

    // each set in the set of links defines one strongly connected component (scc),
    // where each member of the scc is bidirectionally connected to every other member of the scc
    links.forEach { scc ->
        for (a in scc) {
            for (b in scc) {
                if (a == b) continue
                map.getOrPut(a) { mutableSetOf() }.add(b)
                map.getOrPut(b) { mutableSetOf() }.add(a)
            }
        }
    }
    return map
}

/**
 * Session manager to create and retrieve sessions.
 */
class SessionManager(val orchestrator: Orchestrator = Orchestrator(), val port: UShort) {
    private val sessions = ConcurrentHashMap<String, CoralAgentGraphSession>()
    private val sessionSemaphore = Semaphore(1)

    /**
     * Create a new session with a random ID.
     */
    fun createSession(applicationId: String, privacyKey: String, agentGraph: AgentGraph?): CoralAgentGraphSession {
        val sessionId = java.util.UUID.randomUUID().toString()

        val subgraphs = agentGraph?.let { it ->

            val adj = it.adjacencyMap()
            val visited = mutableSetOf<String>()
            val subgraphs = mutableListOf<Set<String>>()

            // flood fill to find all disconnected subgraphs
            for (node in adj.keys) {
                if (visited.contains(node)) continue
                // non-blocking agents should not be considered part of any subgraph
                if(it.agents[AgentName(node)]?.blocking == false) continue

                val subgraph = mutableSetOf(node)
                val toVisit = adj[node]?.toMutableList()
                while (toVisit?.isNotEmpty() == true) {
                    val next = toVisit.removeLast()
                    if(visited.contains(next)) continue
                    // non-blocking agents should not be considered part of any subgraph
                    if(it.agents[AgentName(next)]?.blocking == false) continue
                    subgraph.add(next)
                    visited.add(next)
                    adj[next]?.let { n -> toVisit.addAll(n) }
                }
                subgraphs.add(subgraph)
                visited.add(node)
            }

            it.agents.forEach { agent ->
                orchestrator.spawn(agent.value, "http://localhost:${port}/${applicationId}/${privacyKey}/${sessionId}/sse?agentId=${agent.key}")
            }
            subgraphs
        }

        val session = CoralAgentGraphSession(sessionId, applicationId, privacyKey, groups = subgraphs?.toList() ?: emptyList())
        sessions[sessionId] = session

        return session
    }

    /**
     * Create a new session with a specific ID.
     */
    fun createSessionWithId(sessionId: String, applicationId: String, privacyKey: String): CoralAgentGraphSession {
        val session = CoralAgentGraphSession(sessionId, applicationId, privacyKey)
        sessions[sessionId] = session
        return session
    }

    /**
     * Get or create a session with a specific ID.
     * If the session exists, return it. Otherwise, create a new one.
     */
    suspend fun getOrCreateSession(
        sessionId: String,
        applicationId: String,
        privacyKey: String
    ): CoralAgentGraphSession {
        sessionSemaphore.withPermit {
            return sessions[sessionId] ?: createSessionWithId(sessionId, applicationId, privacyKey)
        }
    }

    /**
     * Get a session by ID.
     */
    fun getSession(sessionId: String): CoralAgentGraphSession? {
        return sessions[sessionId]
    }

    /**
     * Get all sessions.
     */
    fun getAllSessions(): List<CoralAgentGraphSession> {
        return sessions.values.toList()
    }
}