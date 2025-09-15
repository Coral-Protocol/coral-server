package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.toRemote
import org.coralprotocol.coralserver.agent.payment.PaidAgent
import org.coralprotocol.coralserver.agent.payment.toMicroCoral
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.config.CORAL_MAINNET_MINT
import org.coralprotocol.coralserver.config.Config
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.utils.SessionIdUtils
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.models.SessionInfo
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun AgentGraph.adjacencyMap(): Map<String, Set<String>> {
    val map = mutableMapOf<String, MutableSet<String>>()

    // each set in the set of links defines one strongly connected component (scc),
    // where each member of the scc is bidirectionally connected to every other member of the scc
    groups.forEach { scc ->
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
class LocalSessionManager(
    val config: Config = Config(),
    val orchestrator: Orchestrator,
    val blockchainService: BlockchainService?,
    val jupiterService: JupiterService
) {
    private val sessions = ConcurrentHashMap<String, LocalSession>()
    private val sessionSemaphore = Semaphore(1)

    private val sessionListeners = ConcurrentHashMap<String, MutableList<CompletableDeferred<Boolean>>>()

    suspend fun waitForSession(id: String, timeoutMs: Long = 10000): LocalSession? {
        if (sessions.containsKey(id)) return sessions[id]
        val deferred = CompletableDeferred<Boolean>()
        sessionListeners.computeIfAbsent(id) { mutableListOf() }.add(deferred)

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: false

        if (!result) {
            // If the wait timed out, remove this deferred from the list
            sessionListeners[id]?.let {
                it.remove(deferred)
                // If the list is now empty, remove the target count from the map
                if (it.isEmpty()) {
                    sessionListeners.remove(id)
                }
            }
        }

        return sessions[id]
    }

    /**
     * Create a new session with a random ID.
     */
    suspend fun createSession(applicationId: String, privacyKey: String, agentGraph: AgentGraph? = null,
                              incomingSessionInfo: SessionInfo? = null): LocalSession =
        createSessionWithId(UUID.randomUUID().toString(), applicationId, privacyKey, agentGraph)

    /**
     * This function should be called on any agent graph that contains remote agents.  This function is responsible for
     * starting the transaction required to pay for the requested remote agents.
     *
     * This function will:
     * - Choose the best server to provide a remote agent
     *      * this can fail, if all servers reject us or our desired max payment
     *      * this can also fail if no servers at all were found to provide the remote agent
     * - Replace the RemoteRequest provider type with the Remote provider type, gathering:
     *      * The chosen server's wallet address
     *      * The chosen server's actual max cost
     */
    suspend fun createPaymentSession(
        agentGraph: AgentGraph
    ): SessionInfo? {
        val paymentGraph = agentGraph.toPayment()
        if (paymentGraph.paidAgents.isEmpty())
            return null

        if (blockchainService == null)
            throw IllegalStateException("Payment services are disabled")

        val paymentSessionId = UUID.randomUUID().toString()
        val agents = mutableListOf<PaidAgent>()

        var fundAmount = 0L
        for (agent in paymentGraph.paidAgents) {
            val id = agent.registryAgent.info.identifier
            val provider = agent.provider
            if (provider !is GraphAgentProvider.RemoteRequest)
                throw IllegalArgumentException("createPaymentSession given non remote agent ${agent.name}")

            fundAmount += provider.maxCost.toMicroCoral(jupiterService)
            val resolvedRemote = provider.toRemote(id, paymentSessionId, jupiterService)

            agents.add(PaidAgent(
                id = agent.name,
                cap = provider.maxCost.toMicroCoral(jupiterService),
                developer = resolvedRemote.wallet
            ))

            // Important! Replace the RemoteRequest with the resolved Remote type
            agent.provider = resolvedRemote
        }

        // todo: add fundAmount when thing
        // todo: Take mintPubkey from session
        return blockchainService.createEscrowSession(
            agents = agents.map { it.toBlockchainModel() },
            mintPubkey = CORAL_MAINNET_MINT,
            sessionId = SessionIdUtils.uuidToSessionId(SessionIdUtils.generateSessionUuid())
        ).getOrThrow()
    }

    /**
     * Create a new session with a specific ID.
     */
    suspend fun createSessionWithId(
        sessionId: String,
        applicationId: String,
        privacyKey: String,
        agentGraph: AgentGraph? = null, // Nullable for devmode
        incomingSessionInfo: SessionInfo? = null // Can pass in for tests :3
    ): LocalSession {
        val sessionInfo = incomingSessionInfo ?: agentGraph?.let { createPaymentSession(it) }
        val subgraphs = agentGraph?.let { agentGraph ->
            val adj = agentGraph.adjacencyMap()
            val visited = mutableSetOf<String>()
            val subgraphs = mutableListOf<Set<String>>()

            // flood fill to find all disconnected subgraphs
            for (node in adj.keys) {
                if (visited.contains(node)) continue
                // non-blocking agents should not be considered part of any subgraph
                if (agentGraph.agents[node]?.blocking == false) continue

                val subgraph = mutableSetOf(node)
                val toVisit = adj[node]?.toMutableList()
                while (toVisit?.isNotEmpty() == true) {
                    val next = toVisit.removeLast()
                    if (visited.contains(next)) continue
                    // non-blocking agents should not be considered part of any subgraph
                    if (agentGraph.agents[next]?.blocking == false) continue
                    subgraph.add(next)
                    visited.add(next)
                    adj[next]?.let { n -> toVisit.addAll(n) }
                }
                subgraphs.add(subgraph)
                visited.add(node)
            }

            subgraphs
        }

        val session = LocalSession(
            id = sessionId,
            paymentSessionId = sessionInfo?.sessionId,
            applicationId = applicationId,
            privacyKey = privacyKey,
            agentGraph = agentGraph,
            groups = subgraphs?.toList() ?: emptyList(),
        )

        session.sessionClosedFlow.onEach {
            cleanupSession(session, it)
        }.launchIn(session.coroutineScope)

        sessions[sessionId] = session

        agentGraph?.agents?.forEach { agent ->
            orchestrator.spawn(
                session = session,
                graphAgent = agent.value,
                agentName = agent.key,
                applicationId,
                privacyKey,
            )
        }

        sessionListeners[sessionId]?.let { it ->
            it.forEach {
                if (!it.isCompleted) {
                    it.complete(true)
                }
            }
        }
        return session
    }


    /**
     * Get or create a session with a specific ID.
     * If the session exists, return it. Otherwise, create a new one.
     */
    suspend fun getOrCreateSession(
        sessionId: String,
        applicationId: String,
        privacyKey: String,
        agentGraph: AgentGraph? = null,
        incomingSessionInfo: SessionInfo? = null
    ): LocalSession {
        sessionSemaphore.withPermit {
            return sessions[sessionId] ?: createSessionWithId(sessionId, applicationId, privacyKey, agentGraph, incomingSessionInfo)
        }
    }

    /**
     * Get a session by ID.
     */
    fun getSession(sessionId: String): LocalSession? {
        return sessions[sessionId]
    }

    /**
     * Get all sessions.
     */
    fun getAllSessions(): List<LocalSession> {
        return sessions.values.toList()
    }

    /**
     * Cleans up all data related to a session
     */
    private suspend fun cleanupSession(session: LocalSession, sessionCloseMode: SessionCloseMode) {
        orchestrator.killForSession(session.id, sessionCloseMode)
    }
}
