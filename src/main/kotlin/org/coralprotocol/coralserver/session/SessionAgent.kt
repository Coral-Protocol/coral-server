package org.coralprotocol.coralserver.session

import io.ktor.server.application.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.ServerSession
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.config.SessionConfig
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.llmproxy.AtomicTokenUsage
import org.coralprotocol.coralserver.llmproxy.TokenUsage
import org.coralprotocol.coralserver.logging.LoggingTag
import org.coralprotocol.coralserver.mcp.McpInstructionSnippet
import org.coralprotocol.coralserver.mcp.McpResourceName
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.session.state.SessionAgentState
import org.coralprotocol.coralserver.x402.X402BudgetedResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap

import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.time.measureTimedValue

typealias SessionAgentSecret = String

/**
 * Contains all runtime information for one agent in a [LocalSession].  Every session has one [AgentGraph], containing
 * one or more [GraphAgent]s.  Each [GraphAgent] will create a pairing [SessionAgent] which will represent that agent
 * for the lifetime for the session.
 *
 * This class also provides (by extension) the MCP [Server] instance that the agent process connects to.  This server
 * will only ever have the matching agent connected to it, and this is enforced by the cryptographically secure
 * [GraphAgent.secret] field, which is unique for every agent in the [AgentGraph].  Connections to the MCP server must
 * provide this secret.
 *
 * Note: the agent process orchestrated for an agent may make multiple connections to its MCP server, this is not a
 * feature but a solution to some frameworks and poorly designed agents making multiple connections to the same server.
 */
class SessionAgent(
    val session: LocalSession,
    val graphAgent: GraphAgent,
    namespace: LocalSessionNamespace,
    sessionManager: LocalSessionManager
) : Server(
    Implementation(
        name = "Coral Agent Server",
        version = "1.0.0"
    ),
    ServerOptions(
        capabilities = ServerCapabilities(
            prompts = ServerCapabilities.Prompts(listChanged = true),
            resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
            tools = ServerCapabilities.Tools(listChanged = true),
        )
    ),
), KoinComponent {
    private val sessionConfig by inject<SessionConfig>()
    val logger = session.logger.withTags(LoggingTag.Agent(graphAgent.name))

    val coroutineScope: CoroutineScope = session.sessionScope

    /**
     * Agent name
     */
    val name: UniqueAgentName = graphAgent.name

    /**
     * A unique secret for this agent, this is used to authenticate agent -> server communication
     */
    val secret: SessionAgentSecret = sessionManager.issueAgentSecret(session, namespace, this)

    /**
     * Default description, this description may be changed when the agent connects to the MCP server and specifies a
     * description as a path parameter
     */
    var description = graphAgent.description ?: graphAgent.registryAgent.description

    /**
     * Connections to other agents.  These connections should be built using groups specified in [AgentGraph.groups]
     */
    val links = mutableSetOf<SessionAgent>()

    /**
     * Cumulative token usage from LLM proxy calls, broken down by provider/model.
     */
    private val _tokensByModel = ConcurrentHashMap<String, AtomicTokenUsage>()
    val tokensByModel: Map<String, TokenUsage>
        get() = _tokensByModel.mapValues { it.value.snapshot() }

    fun accumulateTokens(provider: String, model: String?, input: Long?, output: Long?) {
        if (input != null || output != null) {
            val key = "$provider/${model ?: "unknown"}"
            _tokensByModel.computeIfAbsent(key) { AtomicTokenUsage() }.add(input, output)
        }
    }

    /**
     * A list of all ongoing waits this agent is performing
     */
    val waiters = MutableStateFlow<List<SessionAgentWaiter>>(listOf())

    /**
     * The full status of this agent.  This is a nested status type: runtime -> connection -> waiting/sleeping/thinking.
     *
     * This means that an agent that is not connected cannot be waiting, sleeping or thinking.  An agent that is not
     * running cannot be connected.
     */
    var status: MutableStateFlow<SessionAgentStatus> = MutableStateFlow(SessionAgentStatus.Waiting)

    /**
     * A list of connected transports for this agent
     * @see connectTransport
     */
    val mcpSessions = ConcurrentHashMap<String, ServerSession>()

    /**
     * The number of *potential* mcp sessions.  Note that this number will increase before the client is accepted, to
     * facilitate blocking agents.
     */
    private val mcpSessionCount = MutableStateFlow(0)

    /**
     * A list of resources that this agent has access to, each resource constrained by a budget.  This is used for x402
     * forwarding, an experimental feature.
     *
     * @see X402BudgetedResource
     */
    val x402BudgetedResources: List<X402BudgetedResource> = listOf()

    /**
     * Everything to do with running this agent is done in this class.
     * @see SessionAgentExecutionContext
     */
    private val executionContext = SessionAgentExecutionContext(this, get())

    /**
     * A list of all required instruction snippets.  This list is populated by calls to [addMcpTool].  The snippets are
     * then presented to the client using the Instruction resource, see [handleInstructionResource]
     */
    private val requiredInstructionSnippets = mutableSetOf<McpInstructionSnippet>()

    /**
     * Accessor for usage reports managed by the execution context
     */
    val usageReports
        get() = executionContext.usageReports.toList()

    init {
        val mcpToolManager: McpToolManager = get()
        addMcpTool(mcpToolManager.createThreadTool)
        addMcpTool(mcpToolManager.closeThreadTool)
        addMcpTool(mcpToolManager.addParticipantTool)
        addMcpTool(mcpToolManager.removeParticipantTool)
        addMcpTool(mcpToolManager.sendMessageTool)
        addMcpTool(mcpToolManager.waitForMessageTool)
        addMcpTool(mcpToolManager.waitForMentionTool)
        addMcpTool(mcpToolManager.waitForAgentMessageTool)

        addResource(
            name = "Instructions",
            description = "Instructions resource",
            uri = McpResourceName.INSTRUCTION_RESOURCE_URI.toString(),
            mimeType = "text/markdown",
            readHandler = { handleInstructionResource(it) }
        )

        addResource(
            name = "State",
            description = "State resource",
            uri = McpResourceName.STATE_RESOURCE_URI.toString(),
            mimeType = "text/markdown",
            readHandler = { handleStateResource(it) }
        )

        graphAgent.plugins.forEach { it.install(this) }
        graphAgent.customTools.forEach { (name, tool) ->
            addTool(
                Tool(
                    name = name,
                    description = tool.description,
                    inputSchema = tool.inputSchema,
                    outputSchema = tool.outputSchema,
                    title = tool.title,
                    annotations = tool.annotations,
                )
            ) {
                tool.transport.execute(name, this@SessionAgent, it)
            }
        }
    }

    /**
     * Helper function for setting the connection status as connected
     *
     * The agent's status will only be updated by this function if the agent's previous status was running.
     */
    fun setConnectionStatusConnected() {
        status.update {
            if (it !is SessionAgentStatus.Running) {
                logger.warn { "cannot set the connection status of an agent that is not running, runtime status is: $it" }
                it
            } else {
                //todo: when sleeping is implemented this should not default to the thinking state, but the default
                //      sleep state
                SessionAgentStatus.Running(
                    SessionAgentConnectionStatus.Connected(SessionAgentCommunicationStatus.Thinking),
                    it.startTime
                )
            }
        }
    }

    /**
     * Helper function for setting the communication status
     *
     * The agent's status can only be updated by this function if the agent's previous status is running and connected.
     */
    fun setCommunicationStatus(communicationStatus: SessionAgentCommunicationStatus) {
        status.update {
            if (it !is SessionAgentStatus.Running) {
                logger.warn { "cannot set the communication status of an agent that is not running, runtime status is: $it" }
                return@update it
            }

            if (it.connectionStatus !is SessionAgentConnectionStatus.Connected) {
                logger.warn { "cannot set the communication status of an agent that is not connected" }
                return@update it
            }

            logger.info { "communication status ${it.connectionStatus.communicationStatus} -> $communicationStatus" }
            SessionAgentStatus.Running(SessionAgentConnectionStatus.Connected(communicationStatus), it.startTime)
        }
    }

    /**
     * Calls [SseServerTransport.handlePostMessage] on sessions that have legacy sse transports.
     */
    suspend fun handleSsePostMessage(call: ApplicationCall) {
        mcpSessions.values.map { it.transport }.filterIsInstance<SseServerTransport>().forEach { transport ->
            transport.handlePostMessage(call)
        }
    }

    /**
     * This function is called before finishing an SSE connection to this agent's MCP server.  It allows a form of
     * synchronization between agents that are marked as blocking, via [GraphAgent.blocking].  This allows the user to
     * provide *some* protection against agents trying to collaborate before other agents are there to witness their
     * actions.  Note
     *
     * If [GraphAgent.blocking] is false, this function will return immediately.
     * If [GraphAgent.blocking] is true, this function will collect every connected agent using a recursive depth-first
     * search on [links] (that has [GraphAgent.blocking] == true) and call [SessionAgent.waitForMcpConnection] on each
     * of them, returning either when all connected blocking agents are trying to connect to their respective MCP
     * servers, or when the [timeoutMs] is reached.
     */
    suspend fun handleBlocking(timeoutMs: Long = 60_000L) {
        val connectedBlockingAgents = buildSet {
            fun dfs(agent: SessionAgent, visited: MutableSet<SessionAgent> = mutableSetOf()) {
                if (!visited.add(agent)) return

                agent.links.forEach { link ->
                    if (link.graphAgent.blocking == true && link != this@SessionAgent) {
                        add(link)
                        dfs(link, visited)
                    }
                }
            }

            dfs(this@SessionAgent)
        }

        if (graphAgent.blocking != true || connectedBlockingAgents.isEmpty()) {
            logger.info { "sse connection established" }
            setConnectionStatusConnected()
            return
        }

        logger.info { "waiting for blocking agents: ${connectedBlockingAgents.joinToString(", ") { it.name }}" }
        val timeout = withTimeoutOrNull(timeoutMs) {
            connectedBlockingAgents.forEach { it.waitForMcpConnection(timeoutMs / connectedBlockingAgents.size) }
        } == null

        if (timeout)
            logger.warn { "timeout occurred waiting for blocking agents to connect" }
        else {
            logger.info { "sse connection established" }
            setConnectionStatusConnected()
        }
    }

    /**
     * Returns true when the first connection MCP connection is made to this agent
     */
    suspend fun waitForMcpConnection(timeoutMs: Long = 10_000L): Boolean {
        if (mcpSessions.isNotEmpty())
            return true

        return withTimeoutOrNull(timeoutMs) {
            return@withTimeoutOrNull mcpSessionCount.first { it != 0 }
        } != null
    }

    /**
     * Creates a session for this agent from a given transport.  Session information is stored in [mcpSessions] and
     * [mcpSessionCount].  Once the transport closes, the session will be removed from the aforementioned.
     */
    suspend fun <T> connectTransport(transport: T, sessionId: String? = null): T
            where T : AbstractTransport {
        if (mcpSessionCount.value == 0) {
            this.session.events.emit(SessionEvent.AgentConnected(name))
        }

        mcpSessionCount.update { it + 1 }
        handleBlocking()

        val session = createSession(transport)
        val sessionId = sessionId ?: session.sessionId

        transport.onClose {
            mcpSessionCount.update {
                mcpSessions.remove(sessionId)
                mcpSessions.count()
            }
        }
        mcpSessions[sessionId] = session

        return transport
    }

    /**
     * Sends a message to a thread.
     *
     * @param message The message to send.
     * @param threadId The ID of the thread that this message is to be sent in.
     * @param mentions An optional list of agents that should be mentioned in the message.  Mentioning an agent will
     * wake them if they are waiting for mentions, but
     *
     * @throws SessionException.MissingThreadException if [threadId] does not exist in [session].
     * @throws SessionException.MissingAgentException if any of the agents in [mentions] do not exist in [session].
     * @throws SessionException.IllegalThreadMentionException if any of the [mentions] are not participants in the thread or if
     * this agent exists in the [mentions].
     */
    suspend fun sendMessage(
        message: String,
        threadId: ThreadId,
        mentions: Set<UniqueAgentName> = setOf()
    ): SessionThreadMessage {
        // possible SessionException.MissingThreadException
        val thread = session.getThreadById(threadId)

        // possible SessionException.MissingAgentException
        val mentions = mentions.map {
            session.getAgent(it)
        }.toSet()

        val message = thread.addMessage(message, this, mentions)
        return message
    }

    /**
     * Suspends until this agent receives a message that matches all specified [filters].  Returns null if the wait
     * channel closes or timeout is reached.
     *
     * @param replayAfter This can be used to replay messages that have already been received.  Replayed messages will
     * be evaluated against [filters].  If there are no messages that came after [replayAfter] or if no messages that
     * came after [replayAfter] match [filters] then this function will wait normally.
     */
    suspend fun waitForMessage(
        replayAfter: Instant = Clock.System.now(),
        filters: Set<SessionThreadMessageFilter> = setOf(),
        timeoutMs: Long = sessionConfig.defaultWaitTimeout
    ): SessionThreadMessage? {
        val msg = withTimeoutOrNull(timeoutMs) {
            val waiter = SessionAgentWaiter(filters)
            waiters.update { it + waiter }

            val replayMessages = mutableListOf<SessionThreadMessage>()
            getThreads().forEach { thread ->
                replayMessages.addAll(thread.withMessageLock { messages ->
                    messages.filter { it.timestamp >= replayAfter }
                })
            }

            if (filters.isEmpty()) {
                logger.info { "attempting to wait for any message from any agent, replaying messages after $replayAfter" }
            } else
                logger.info { "attempting to wait for a message that matches filters [${filters.joinToString(", ")}], replaying messages after $replayAfter" }

            session.events.emit(SessionEvent.AgentWaitStart(name, filters))
            setCommunicationStatus(SessionAgentCommunicationStatus.WaitingMessage)

            var foundInReplay = false
            if (replayMessages.isNotEmpty()) {
                val matching = replayMessages.firstOrNull { waiter.matches(it) }
                if (matching != null) {
                    logger.info { "found a matching message in ${replayMessages.size} replayed messages" }
                    foundInReplay = true

                    waiters.update { it - waiter }
                    waiter.deferred.complete(matching)
                } else {
                    logger.info { "no matching messages found in ${replayMessages.size} replayed messages, waiting for ${timeoutMs}ms..." }
                }
            } else
                logger.info { "no messages to replay, waiting for new messages for ${timeoutMs}ms..." }

            val wait = measureTimedValue {
                waiter.deferred.await()
            }

            if (!foundInReplay)
                logger.info { "found matching message: ${wait.value.id} in ${wait.duration}" }

            session.events.emit(SessionEvent.AgentWaitStop(name, wait.value))
            setCommunicationStatus(SessionAgentCommunicationStatus.Thinking)

            wait.value
        }

        if (msg == null) {
            logger.info {
                "timeout of ${timeoutMs.milliseconds} occurred waiting for message that matches ${
                    filters.joinToString(
                        ", "
                    )
                }"
            }

            setCommunicationStatus(SessionAgentCommunicationStatus.Thinking)
        }

        return msg
    }

    /**
     * Adds a tool to this agent's MCP server.  This can be called at any time during the lifetime of the agent.
     */
    fun <In, Out> addMcpTool(tool: McpTool<In, Out>) {
        addTool(
            name = tool.name.toString(),
            description = tool.description,
            inputSchema = tool.inputSchema
        ) { request ->
            tool.execute(this@SessionAgent, request.arguments ?: EmptyJsonObject)
        }

        requiredInstructionSnippets += tool.requiredSnippets
    }

    /**
     * Responds to the MCP read resource of [McpResourceName.INSTRUCTION_RESOURCE_URI] with a string made out of all
     * the snippets ([McpInstructionSnippet]) in [requiredInstructionSnippets].
     */
    private fun handleInstructionResource(request: ReadResourceRequest): ReadResourceResult {
        return ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    text = requiredInstructionSnippets.joinToString("\n\n") { it.snippet },
                    uri = request.uri,
                    mimeType = "text/markdown",
                )
            )
        )
    }

    /**
     * Responds to the MCP read resource of [McpResourceName.STATE_RESOURCE_URI] with various resources describing the
     * observable state of the session from the perspective of this agent.
     *
     * This resource is how the agent knows about past messages, threads and other agents.
     */
    suspend fun handleStateResource(request: ReadResourceRequest): ReadResourceResult {
        return ReadResourceResult(
            contents = listOf(
                TextResourceContents(
                    text = renderState(),
                    uri = request.uri,
                    mimeType = "text/markdown",
                )
            )
        )
    }

    /**
     * Called when a message was posted in a thread that this agent participates in
     */
    fun notifyMessage(message: SessionThreadMessage) {
        val matching = mutableListOf<SessionAgentWaiter>()
        waiters.update { waiters ->
            val (a, b) = waiters.partition { it.matches(message) }
            matching.addAll(a)

            b
        }

        matching.forEach { it.deferred.complete(message) }
    }

    /**
     * Returns a list of all threads that this agent is currently participating in.
     */
    suspend fun getThreads() =
        session.threads.values.filter {
            it.hasParticipant(graphAgent.name)
        }

    /**
     * Returns a list of all messages that this agent can see (from threads that it is participating in)
     */
    suspend fun getVisibleMessages(): List<SessionThreadMessage> {
        val visibleMessages = mutableListOf<SessionThreadMessage>()
        getThreads().forEach { thread ->
            thread.withMessageLock { visibleMessages.addAll(it) }
        }

        return visibleMessages
    }

    /**
     * Launches this agent via [executionContext].
     */
    fun launch() = coroutineScope.launch {
        executionContext.launch()
    }

    /**
     * Returns a JSON object used for describing this agent in ANOTHER agent's state resource.  This should only contain
     * information that is relevant to another agent.
     */
    fun asJsonState(): JsonObject =
        buildJsonObject {
            val currentStatus = status.value
            put("agentName", name)
            put("agentDescription", description)
            put("agentConnected", mcpSessionCount.value != 0)
            put(
                "agentWaiting", currentStatus is SessionAgentStatus.Running &&
                        currentStatus.connectionStatus is SessionAgentConnectionStatus.Connected &&
                        currentStatus.connectionStatus.communicationStatus is SessionAgentCommunicationStatus.WaitingMessage
            )
            put(
                "agentSleeping", currentStatus is SessionAgentStatus.Running &&
                        currentStatus.connectionStatus is SessionAgentConnectionStatus.Connected &&
                        currentStatus.connectionStatus.communicationStatus is SessionAgentCommunicationStatus.Sleeping
            )
            put("agentRunning", currentStatus is SessionAgentStatus.Running)
            if (currentStatus is SessionAgentStatus.Running) {
                put("agentStartTime", currentStatus.startTime.toString())
            } else if (currentStatus is SessionAgentStatus.Stopped) {
                put("agentStartTime", currentStatus.startTime.toString())
            }
        }

    /**
     * Returns the current state of this agent.  Used by the session API.
     */
    fun getState(): SessionAgentState =
        SessionAgentState(
            name = name,
            registryAgentIdentifier = graphAgent.registryAgent.identifier,
            status = status.value,
            description = description,
            links = links.map { it.name }.toSet(),
            annotations = graphAgent.annotations,
            executionProfile = executionContext.executionPolicy.profileName,
            trustTier = executionContext.executionPolicy.trustTier,
            declaredExecution = graphAgent.registryAgent.execution,
            resolvedEgress = executionContext.egressPolicy.allAllowed().toList(),
            sandboxBackend = executionContext.sandboxBackend,
            tokensByModel = tokensByModel
        )

    /**
     * Renders the state of the session from the perspective of this agent.  This should be injected into prompts so
     * that they understand the current Coral-managed state.
     */
    suspend fun renderState(): String {
        val agents = links.map { it.asJsonState() }
        val threads = getThreads().map { it.asJsonState() }

        val agentsText = """
        # Agents
        You collaborate with ${links.size} other agents, described below:
        Consider that they have different contexts and instructions and don't necessarily know what you know unless you tell them.
        ```json
        [${agents.joinToString(",")}]
        ```
        Since you are in close contact with these agents, you will immediately see messages they post to shared threads even without explicitly waiting. It may be better to skip waiting, or call coral wait tools with much lower timeouts (e.g. 2-5 seconds) in order to collaborate in a timely manner with them. 
        """

        val threadsText = """
        # Threads and messages
        You have access to the following threads and their messages:
        
        ```json
        [${threads.joinToString(",")}]
        ```
        """

        var composed = """
        # General
        You are an agent named $name. The current UNIX time is ${System.currentTimeMillis()} (ISO-8601: ${Clock.System.now()}).
        """

        if (agents.isNotEmpty())
            composed += agentsText

        if (threads.isNotEmpty())
            composed += threadsText

        return composed.trimIndent()
    }

    override fun toString(): String {
        return "SessionAgentState(graphAgent=${name}, links=${links.joinToString(", ") { it.name }})"
    }
}
