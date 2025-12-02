package org.coralprotocol.coralserver.session

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.toRemote
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.payment.PaidAgent
import org.coralprotocol.coralserver.agent.payment.toMicroCoral
import org.coralprotocol.coralserver.agent.payment.toUsd
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
//import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.config.CORAL_MAINNET_MINT
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.utils.SessionIdUtils
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.models.SessionInfo
import java.util.UUID

data class LocalSessionNamespace(
    val name: String,
    val sessions: MutableMap<String, LocalSession>,
)

data class AgentLocator(
    val namespace: LocalSessionNamespace,
    val session: LocalSession,
    val agent: SessionAgent
)

private val logger = KotlinLogging.logger {  }

class LocalSessionManager(
    val blockchainService: BlockchainService? = null,

    // Default value will not provide a Docker runtime
    val applicationRuntimeContext: ApplicationRuntimeContext = ApplicationRuntimeContext(),
    val jupiterService: JupiterService
) {
    val managementScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Main data structure containing all sessions
     */
    private val sessionNamespaces = mutableMapOf<String, LocalSessionNamespace>()

    /**
     * Helper structure for looking up agents by their secret.  This should return an [AgentLocator] which contains the
     * exact namespace and session that the agent is in.
     */
    private val agentSecretLookup = mutableMapOf<SessionAgentSecret, AgentLocator>()

    /**
     * Issues a secret for an agent.  This is the only function that should generate agent secrets, so that all agent
     * secrets can be mapped to locations in the [agentSecretLookup] map.
     */
    fun issueAgentSecret(session: LocalSession, namespace: LocalSessionNamespace, agent: SessionAgent): SessionAgentSecret {
        val secret: SessionAgentSecret = UUID.randomUUID().toString()
        agentSecretLookup[secret] = AgentLocator(
            namespace = namespace,
            session = session,
            agent = agent
        )

        return secret
    }

    /**
     * Creates a payment session for an [AgentGraph] if [blockchainService] is not null (meaning wallet information was
     * set up on the server) and there are paid agents in the graph.  Null will be returned otherwise.
     */
    suspend fun createPaymentSession(agentGraph: AgentGraph): SessionInfo? {
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

            val maxCostMicro = provider.maxCost.toMicroCoral(jupiterService)
            fundAmount += maxCostMicro

            val resolvedRemote = provider.toRemote(id, paymentSessionId, jupiterService)

            agents.add(PaidAgent(
                id = agent.name,
                cap = maxCostMicro,
                developer = resolvedRemote.wallet
            ))

            // Important! Replace the RemoteRequest with the resolved Remote type
            agent.provider = resolvedRemote
        }

        val maxCostUsd = AgentClaimAmount.MicroCoral(fundAmount).toUsd(jupiterService)
        logger.info { "Created funded payment session with maxCost = $fundAmount ($maxCostUsd USD)" }

        return blockchainService.createAndFundEscrowSession(
            agents = agents.map { it.toBlockchainModel() },
            mintPubkey = CORAL_MAINNET_MINT,
            sessionId = SessionIdUtils.uuidToSessionId(SessionIdUtils.generateSessionUuid()),
            fundingAmount = fundAmount,
        ).getOrThrow()
    }

    /**
     * Creates a session in [namespace].  The namespace will be created if it does not exist.  This function will not
     * launch any agents!  See [createAndLaunchSession] if you want to one-call session creation and launching.
     */
    suspend fun createSession(namespace: String, agentGraph: AgentGraph): Pair<LocalSession, LocalSessionNamespace> {
        val namespace = sessionNamespaces.getOrPut(namespace) {
            LocalSessionNamespace(namespace, mutableMapOf())
        }

        val sessionId: SessionId = UUID.randomUUID().toString()
        val session = LocalSession(
            id = sessionId,
            namespace = namespace,
            paymentSessionId = createPaymentSession(agentGraph)?.sessionId,
            agentGraph = agentGraph,
            sessionManager = this,
        )
        namespace.sessions[sessionId] = session

        return Pair(session, namespace)
    }

    /**
     * Helper function, calls [createSession] and then immediately launches all agents in the session.  After the
     * session closes, [handleSessionClose] will be called.
     */
    suspend fun createAndLaunchSession(namespace: String, agentGraph: AgentGraph) {
        val (session, namespace) = createSession(namespace, agentGraph)
        session.launchAgents()

        managementScope.launch {
            session.joinAgents()
        }.invokeOnCompletion {
            handleSessionClose(session, namespace, it)
        }
    }

     /**
     * Locates an agent by the agent's secret.
     *
     * @throws SessionException.InvalidAgentSecret if the secret does not map to an agent
     */
    fun locateAgent(secret: SessionAgentSecret) =
        agentSecretLookup[secret]
            ?: throw SessionException.InvalidAgentSecret("The provided agent secret is not valid")

    /**
     * Returns a list of sessions in the specified namespace.
     *
     * @throws SessionException.InvalidNamespace if the namespace does not exist
     */
    fun getSessions(namespace: String) =
        sessionNamespaces[namespace]?.sessions?.values ?: SessionException.InvalidNamespace("The provided namespace does not exist")

    /**
     * Returns a list of registered namespaces
     */
    fun getNamespaces() =
        sessionNamespaces.values.toList()

    fun handleSessionClose(
        session: LocalSession,
        namespace: LocalSessionNamespace,
        cause: Throwable?
    ) {
        // Secrets must be relinquished so that no more references to this session exist
        session.agents.forEach { (name, agent) ->
            agentSecretLookup.remove(agent.secret)
        }

        namespace.sessions.remove(session.id)
        if (namespace.sessions.isEmpty())
            sessionNamespaces.remove(namespace.name)
    }

    /**
     * Waits for every agent of every session to exit.  Note this function does not kill anything.
     */
    suspend fun waitAllSessions() {
        sessionNamespaces.values.forEach { namespace ->
            namespace.sessions.values.forEach { session ->
                session.joinAgents()
            }
        }
    }
}