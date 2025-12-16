package org.coralprotocol.coralserver.session

//import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.toRemote
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.payment.PaidAgent
import org.coralprotocol.coralserver.agent.payment.toMicroCoral
import org.coralprotocol.coralserver.agent.payment.toUsd
import org.coralprotocol.coralserver.agent.runtime.ApplicationRuntimeContext
import org.coralprotocol.coralserver.config.CORAL_MAINNET_MINT
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.utils.SessionIdUtils
import org.coralprotocol.coralserver.session.models.SessionRuntimeSettings
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.models.SessionInfo
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class LocalSessionNamespace(
    val name: String,
    // todo: make a kotlin version of this
    val sessions: ConcurrentHashMap<String, LocalSession>,
)

data class AgentLocator(
    val namespace: LocalSessionNamespace,
    val session: LocalSession,
    val agent: SessionAgent
)

private val logger = KotlinLogging.logger { }

class LocalSessionManager(
    val blockchainService: BlockchainService? = null,

    // Default value will not provide a Docker runtime
    val applicationRuntimeContext: ApplicationRuntimeContext = ApplicationRuntimeContext(),
    val jupiterService: JupiterService,
    val mcpToolManager: McpToolManager = McpToolManager(),
    val managementScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val supervisedSessions: Boolean = true
) {

    /**
     * Main data structure containing all sessions
     * todo: make a kotlin version of this
     */
    private val sessionNamespaces = ConcurrentHashMap<String, LocalSessionNamespace>()

    /**
     * Helper structure for looking up agents by their secret.  This should return an [AgentLocator] which contains the
     * exact namespace and session that the agent is in.
     * todo: make a kotlin version of this
     */
    private val agentSecretLookup = ConcurrentHashMap<SessionAgentSecret, AgentLocator>()

    /**
     * Issues a secret for an agent.  This is the only function that should generate agent secrets, so that all agent
     * secrets can be mapped to locations in the [agentSecretLookup] map.
     */
    fun issueAgentSecret(
        session: LocalSession,
        namespace: LocalSessionNamespace,
        agent: SessionAgent
    ): SessionAgentSecret {
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
            val id = agent.registryAgent.identifier
            val provider = agent.provider
            if (provider !is GraphAgentProvider.RemoteRequest)
                throw IllegalArgumentException("createPaymentSession given non remote agent ${agent.name}")

            val maxCostMicro = provider.maxCost.toMicroCoral(jupiterService)
            fundAmount += maxCostMicro

            val resolvedRemote = provider.toRemote(id, paymentSessionId, jupiterService)

            agents.add(
                PaidAgent(
                    id = agent.name,
                    cap = maxCostMicro,
                    developer = resolvedRemote.wallet
                )
            )

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
            LocalSessionNamespace(namespace, ConcurrentHashMap())
        }

        val sessionId: SessionId = UUID.randomUUID().toString()
        val session = LocalSession(
            id = sessionId,
            namespace = namespace,
            paymentSessionId = createPaymentSession(agentGraph)?.sessionId,
            agentGraph = agentGraph,
            sessionManager = this,
            mcpToolManager = mcpToolManager
        )
        namespace.sessions[sessionId] = session

        return Pair(session, namespace)
    }

    /**
     * Helper function, calls [createSession] and then immediately launches all agents in the session.  After the
     * session closes, [handleSessionClose] will be called.
     */
    suspend fun createAndLaunchSession(
        namespace: String,
        agentGraph: AgentGraph,
        settings: SessionRuntimeSettings
    ): Pair<LocalSession, LocalSessionNamespace> {
        val (session, namespace) = createSession(namespace, agentGraph)
        session.launchAgents()

        val timeout = settings.ttl?.milliseconds ?: Duration.INFINITE

        managementScope.launch {
            withTimeout(timeout) {
                session.joinAgents()
            }
        }.invokeOnCompletion {
            managementScope.launch {
                handleSessionClose(session, namespace, it, settings)
            }
        }

        return Pair(session, namespace)
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
        sessionNamespaces[namespace]?.sessions?.values?.toList()
            ?: throw SessionException.InvalidNamespace("The provided namespace does not exist")

    /**
     * Returns a list of registered namespaces
     */
    fun getNamespaces() =
        sessionNamespaces.values.toList()

    /**
     * Behaviour for session exit.
     *
     * @param session The session that exited.
     * @param namespace The namespace that the session was in.
     * @param cause The reason the session exited.
     * @param settings The settings used to create the session.
     */
    suspend fun handleSessionClose(
        session: LocalSession,
        namespace: LocalSessionNamespace,
        cause: Throwable?,
        settings: SessionRuntimeSettings
    ) {
        if (cause is TimeoutCancellationException) {
            logger.warn { "session ${session.id} timed out" }
        }

        // Secrets must be relinquished so that no more references to this session exist
        session.agents.forEach { (name, agent) ->
            agentSecretLookup.remove(agent.secret)
        }

        // If the session had a TTL and wanted to stay alive for that long, delay the deletion of the session (and
        // possibly namespace) until after the TTL would have exited.  Note that it is intentional that the agent
        // secrets are released before this delay
        if (settings.ttl != null && settings.holdForTtl) {
            val remainingTime = (session.timestamp + settings.ttl) - System.currentTimeMillis();
            logger.info { "holding session ${session.id} in memory for $remainingTime milliseconds" }
            delay(remainingTime)
        }

        namespace.sessions.remove(session.id)
        if (namespace.sessions.isEmpty())
            sessionNamespaces.remove(namespace.name)
    }

    /**
     * Waits for every agent of every session to exit.  Note this function does not kill anything.
     */
    suspend fun waitAllSessions(cancel: Boolean = false) {
        sessionNamespaces.values.forEach { namespace ->
            namespace.sessions.values.forEach { session ->
                if (cancel) {
                    session.cancelAndJoinAgents()
                } else {
                    session.joinAgents()
                }
            }
        }
    }
}