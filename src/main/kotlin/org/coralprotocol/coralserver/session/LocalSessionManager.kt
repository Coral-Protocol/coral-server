@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.session

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.toRemote
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.payment.PaidAgent
import org.coralprotocol.coralserver.agent.payment.toMicroCoral
import org.coralprotocol.coralserver.agent.payment.toUsd
import org.coralprotocol.coralserver.config.CORAL_MAINNET_MINT
import org.coralprotocol.coralserver.config.NetworkConfig
import org.coralprotocol.coralserver.events.LocalSessionManagerEvent
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.payment.BlankBlockchainService
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.utils.SessionIdUtils
import org.coralprotocol.coralserver.session.reporting.SessionEndReport
import org.coralprotocol.coralserver.session.state.SessionNamespaceStateBase
import org.coralprotocol.coralserver.session.state.SessionNamespaceStateExtended
import org.coralprotocol.coralserver.session.state.SessionState
import org.coralprotocol.coralserver.util.addJsonBodyWithSignature
import org.coralprotocol.coralserver.util.utcTimeNow
import org.coralprotocol.payment.blockchain.BlockchainService
import org.coralprotocol.payment.blockchain.models.SessionInfo
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

data class LocalSessionNamespace(
    val name: String,
    val deleteOnLastSessionExit: Boolean,
    var deleted: Boolean = false,

    // todo: make a kotlin version of this
    val sessions: ConcurrentHashMap<String, LocalSession>,

    override val annotations: Map<String, String>,
) : SessionResource {
    fun getState() =
        SessionNamespaceStateExtended(
            base = SessionNamespaceStateBase(
                name = name,
                deleteOnLastSessionExit = deleteOnLastSessionExit,
                annotations = annotations
            ),
            sessions = sessions.values.map { it.getState().base }
        )
}

data class AgentLocator(
    val namespace: LocalSessionNamespace,
    val session: LocalSession,
    val agent: SessionAgent
)

class LocalSessionManager(
    private val blockchainService: BlockchainService,
    private val jupiterService: JupiterService,
    private val httpClient: HttpClient,
    private val config: NetworkConfig,
    private val json: Json,
    private val logger: Logger,
    val managementScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val supervisedSessions: Boolean = true,
) {
    /**
     * Events emitted by this manager.  Related to session or namespace creation or deletion.
     */
    val events = MutableSharedFlow<LocalSessionManagerEvent>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        extraBufferCapacity = 4096,
    )

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

        if (blockchainService is BlankBlockchainService)
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

            val resolvedRemote = provider.toRemote(id, paymentSessionId)

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
     * Creates a new namespace with settings specified by a [SessionNamespaceRequest]
     *
     * @throws SessionException.InvalidNamespace if name specified in [SessionNamespaceRequest.name] is already taken
     */
    suspend fun createNamespace(request: SessionNamespaceRequest): LocalSessionNamespace {
        if (sessionNamespaces.containsKey(request.name))
            throw SessionException.InvalidNamespace("A namespace with name \"${request.name}\" already exists")

        val namespace = LocalSessionNamespace(
            name = request.name,
            deleteOnLastSessionExit = request.deleteOnLastSessionExit,
            sessions = ConcurrentHashMap(),
            annotations = request.annotations
        )
        sessionNamespaces[request.name] = namespace
        events.emit(LocalSessionManagerEvent.NamespaceCreated(namespace.getState().base))

        return namespace
    }

    /**
     * Creates a session in [namespace].  This function will not launch any agents!  See [createAndLaunchSession] if
     * you want to one-call session creation and launching.
     */
    suspend fun createSession(
        namespace: LocalSessionNamespace,
        agentGraph: AgentGraph,
        sessionAnnotations: Map<String, String> = mapOf()
    ): Pair<LocalSession, LocalSessionNamespace> {
        val sessionId: SessionId = UUID.randomUUID().toString()
        val session = LocalSession(
            id = sessionId,
            namespace = namespace,
            paymentSessionId = createPaymentSession(agentGraph)?.sessionId,
            agentGraph = agentGraph,
            sessionManager = this,
            annotations = sessionAnnotations
        )
        namespace.sessions[sessionId] = session
        events.emit(
            LocalSessionManagerEvent.SessionCreated(
                session.getState().base,
                namespace.getState().base
            )
        )

        return Pair(session, namespace)
    }

    /**
     * Helper function for dynamically creating a basic namespace from a string
     */
    suspend fun createSession(
        namespaceName: String,
        agentGraph: AgentGraph,
        sessionAnnotations: Map<String, String> = mapOf()
    ): Pair<LocalSession, LocalSessionNamespace> {
        val namespace = createNamespace(SessionNamespaceRequest(name = namespaceName))
        return createSession(namespace, agentGraph, sessionAnnotations)
    }

    /**
     * Launches an existing session on [managementScope]
     */
    fun launchSession(session: LocalSession, namespace: LocalSessionNamespace, settings: SessionRuntimeSettings) {
        managementScope.launch {
            val timeoutDuration = settings.ttl?.milliseconds ?: Duration.INFINITE
            val timedOut = withTimeoutOrNull(timeoutDuration) {
                events.emit(LocalSessionManagerEvent.SessionRunning(session.getState().base, namespace.getState().base))
                session.status.update { SessionStatus.Running(utcTimeNow()) }

                session.launchAgents()
                session.joinAgents()
            } == null

            if (timedOut) {
                logger.warn { "session ${session.id} reached $timeoutDuration timeout" }
                session.cancelAndJoinAgents()
            }
        }.invokeOnCompletion {
            managementScope.launch {
                handleSessionClose(session, namespace, it, settings)
            }
        }
    }

    /**
     * Helper function, calls [createSession] and then immediately launches all agents in the session.  After the
     * session closes, [handleSessionClose] will be called.
     */
    suspend fun createAndLaunchSession(
        namespace: LocalSessionNamespace,
        agentGraph: AgentGraph,
        settings: SessionRuntimeSettings = SessionRuntimeSettings(),
        sessionAnnotations: Map<String, String> = mapOf()
    ): Pair<LocalSession, LocalSessionNamespace> {
        val (session, namespace) = createSession(namespace, agentGraph, sessionAnnotations)
        launchSession(session, namespace, settings)

        return Pair(session, namespace)
    }

    /**
     * Helper function for dynamically creating a basic namespace from a string
     */
    suspend fun createAndLaunchSession(
        namespaceName: String,
        agentGraph: AgentGraph,
        settings: SessionRuntimeSettings = SessionRuntimeSettings(),
        sessionAnnotations: Map<String, String> = mapOf()
    ): Pair<LocalSession, LocalSessionNamespace> {
        val namespace = createNamespace(SessionNamespaceRequest(name = namespaceName))
        return createAndLaunchSession(namespace, agentGraph, settings, sessionAnnotations)
    }

    /**
     * Helper function for closing all sessions in a namespace, then deleting the namespace (even if
     * deleteOnLastSessionExit is false for this namespace)
     */
    suspend fun deleteNamespace(namespaceName: String) {
        val namespace = getNamespace(namespaceName)
        namespace.deleted = true
        namespace.sessions.values.forEach { session ->
            session.cancelAndJoinAgents()
        }

        // It's important that this function doesn't return until the namespace is deleted, even if
        // deleteOnLastSessionExit is true, that logic is performed on the session's invokeOnCompletion callback, so it
        // is possible the above code for cancelling and joining agents in the sessions does NOT delete the namespace
        //
        // namespace must be marked as deleted too to avoid double deletion
        events.emit(LocalSessionManagerEvent.NamespaceClosed(namespace.getState().base))
        sessionNamespaces.remove(namespace.name)
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
    fun getSessions(namespaceName: String) =
        sessionNamespaces[namespaceName]?.sessions?.values?.toList()
            ?: throw SessionException.InvalidNamespace("Namespace \"$namespaceName\" does not exist")

    fun getNamespaces() =
        sessionNamespaces.values.toList()

    fun getNamespaceStates() =
        sessionNamespaces.values.map { it.getState() }

    fun getNamespace(namespaceName: String) =
        sessionNamespaces[namespaceName]
            ?: throw SessionException.InvalidNamespace("Namespace \"$namespaceName\" not found")

    fun getSession(namespaceName: String, sessionId: SessionId) =
        getNamespace(namespaceName).sessions[sessionId]
            ?: throw SessionException.InvalidSession("Session \"$sessionId\" not found")

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
        session.status.update {
            if (it is SessionStatus.Running) {
                SessionStatus.Closing(it.executionTime, utcTimeNow())
            } else {
                logger.warn { "session ${session.id} has closed before ever being executed!" }
                SessionStatus.Closing(utcTimeNow(), utcTimeNow())
            }
        }

        // Secrets must be relinquished so that no more references to this session exist
        session.agents.forEach { (name, agent) ->
            agentSecretLookup.remove(agent.secret)
        }

        events.emit(LocalSessionManagerEvent.SessionClosing(session.getState().base, namespace.getState().base))

        // The session end webhook should not block any of the other session ending logic
        if (settings.webhooks.sessionEnd != null) {
            managementScope.launch {
                httpClient.post(settings.webhooks.sessionEnd.url) {
                    addJsonBodyWithSignature(
                        json,
                        config.webhookSecret, SessionEndReport(
                            timestamp = utcTimeNow(),
                            namespaceState = session.namespace.getState().base,
                            sessionState = if (settings.extendedEndReport) {
                                SessionState.Extended(session.getState())
                            } else {
                                SessionState.Base(session.getState().base)
                            },
                            agentStats = session.agents.values.flatMap { it.usageReports },
                        )
                    )
                }
            }
        }

        val delay = when (val mode = settings.persistenceMode) {
            is SessionPersistenceMode.HoldAfterExit -> mode.duration.milliseconds
            is SessionPersistenceMode.MinimumTime -> (session.timestamp + mode.time.milliseconds) - utcTimeNow()
            SessionPersistenceMode.None -> Duration.ZERO
        }

        if (delay > 0.milliseconds) {
            logger.info { "holding session ${session.id} in memory for $delay" }
            delay(delay)
        }


        logger.info { "session ${session.id} closed" }

        events.emit(LocalSessionManagerEvent.SessionClosed(session.getState().base, namespace.getState().base))

        if (!namespace.deleted) {
            namespace.sessions.remove(session.id)
            if (namespace.sessions.isEmpty() && namespace.deleteOnLastSessionExit) {
                events.emit(LocalSessionManagerEvent.NamespaceClosed(namespace.getState().base))
                sessionNamespaces.remove(namespace.name)
            }
        }

        session.sessionScope.cancel()
    }

    /**
     * Waits for every agent of every session to exit.  Note this function does not kill anything.
     */
    suspend fun waitAllSessions() {
        sessionNamespaces.values.forEach { namespace ->
            namespace.sessions.values.forEach { session ->
                session.joinAgents()
                session.sessionScope.coroutineContext[Job]?.join()
            }
        }
    }
}
