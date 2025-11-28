package org.coralprotocol.coralserver.session

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.toRemote
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.payment.PaidAgent
import org.coralprotocol.coralserver.agent.payment.toMicroCoral
import org.coralprotocol.coralserver.agent.payment.toUsd
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
//    val orchestrator: Orchestrator? = null,
    val jupiterService: JupiterService
) {

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
     * Creates a new local session. See [LocalSession] for more information about sessions and [SessionAgent]s
     */
    suspend fun createSession(namespace: String, agentGraph: AgentGraph): LocalSession {
        val namespace = sessionNamespaces.getOrPut(namespace) {
            LocalSessionNamespace(namespace, mutableMapOf())
        }

        val sessionId: SessionId = UUID.randomUUID().toString()
        val session = LocalSession(
            id = sessionId,
            namespace = namespace,
            paymentSessionId = createPaymentSession(agentGraph)?.sessionId,
            agentGraph = agentGraph,
            sessionManager = this
        )
        namespace.sessions[sessionId] = session

//        if (orchestrator != null) {
//            session.agents.values.forEach { agent ->
//                orchestrator.spawn(
//                    session = session,
//                    agent = agent,
//                    agentName = agent.key,
//                    applicationId,
//                    privacyKey,
//                )
//            }
//        }

        return session
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
}