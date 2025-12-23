package org.coralprotocol.coralserver.session

import io.ktor.server.application.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.events.SessionEvent
import org.coralprotocol.coralserver.logging.LoggerWithFlow
import org.coralprotocol.coralserver.mcp.McpInstructionSnippet
import org.coralprotocol.coralserver.mcp.McpResourceName
import org.coralprotocol.coralserver.mcp.McpTool
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.session.state.SessionAgentState
import org.coralprotocol.coralserver.util.ConcurrentMutableList
import org.coralprotocol.coralserver.x402.X402BudgetedResource

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
    sessionManager: LocalSessionManager,
    val mcpToolManager: McpToolManager
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
) {
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
     * LocalSessionManager's copy of the custom tool secret.  This is used to generate signatures for custom tool requests
     * made by this agent
     */
    val customToolSecret = sessionManager.config.networkConfig.customToolSecret

    /**
     * LocalSessionManager's http client
     */
    val httpClient = sessionManager.httpClient

    /**
     * LocalSessionManager's config
     */
    val config = sessionManager.config

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
     * A list of all ongoing waits this agent is performing.  Usually, this will never be more than one at a time.
     * @see ConcurrentMutableList
     */
    val waiters = ConcurrentMutableList<SessionAgentWaiter>()

    /**
     * A list of SSE connections to this agent's MCP server.
     * @see connectSseSession
     */
    private val sseTransports = mutableListOf<SseServerTransport>()

    /**
     * Completed when the first connection to this agent's MCP server is made.
     * @see [waitForSseConnection]
     */
    private val firstConnectionEstablished = CompletableDeferred<SseServerTransport>()

    /**
     * A list of resources that this agent has access to, each resource constrained by a budget.  This is used for x402
     * forwarding, an experimental feature.
     *
     * @see X402BudgetedResource
     */
    val x402BudgetedResources: List<X402BudgetedResource> = listOf()

    /**
     * The logger for this agent.  This will send logging messages both to logback and to a shared flow that clients can
     * subscribe to.
     */
    val logger = LoggerWithFlow("AgentLogger:$name")

    /**
     * Everything to do with running this agent is done in this class.
     * @see SessionAgentExecutionContext
     */
    private val executionContext = SessionAgentExecutionContext(this, sessionManager.applicationRuntimeContext)

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
            readHandler = ::handleInstructionResource
        )

        addResource(
            name = "State",
            description = "State resource",
            uri = McpResourceName.STATE_RESOURCE_URI.toString(),
            mimeType = "text/markdown",
            readHandler = ::handleStateResource
        )

        graphAgent.plugins.forEach { it.install(this) }
        graphAgent.customTools.forEach { (name, tool) ->
            addTool(tool.schema) {
                tool.transport.execute(name, this, it)
            }
        }
    }

    /**
     * Calls [SseServerTransport.handlePostMessage] on all [sseTransports].  This should only be called by the API
     * endpoint associated with an SSE connection to this agent's MCP server.
     */
    suspend fun handlePostMessage(call: ApplicationCall) {
        sseTransports.forEach { transport -> transport.handlePostMessage(call) }
    }

    /**
     * This function is called before finishing an SSE connection to this agent's MCP server.  It allows a form of
     * synchronization between agents that are marked as blocking, via [GraphAgent.blocking].  This allows the user to
     * provide *some* protection against agents trying to collaborate before other agents are there to witness their
     * actions.  Note
     *
     * If [GraphAgent.blocking] is false, this function will return immediately.
     * If [GraphAgent.blocking] is true, this function will collect every connected agent using a recursive depth-first
     * search on [links] (that has [GraphAgent.blocking] == true) and call [SessionAgent.waitForSseConnection] on each
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
            logger.info("sse connection established")
            return
        }

        logger.info("waiting for blocking agents: ${connectedBlockingAgents.joinToString(", ") { it.name }}")
        val timeout = withTimeoutOrNull(timeoutMs) {
            connectedBlockingAgents.forEach { it.waitForSseConnection(timeoutMs / connectedBlockingAgents.size) }
        } == null

        if (timeout)
            logger.warn("timeout occurred waiting for blocking agents to connect")
        else
            logger.info("sse connection established")
    }

    /**
     * Returns true when the first connection SSE connection is made to this agent.  This will not check if that
     * connection is still alive.
     */
    suspend fun waitForSseConnection(timeoutMs: Long = 10_000L): Boolean {
        if (firstConnectionEstablished.isCompleted)
            return true

        return withTimeoutOrNull(timeoutMs) {
            return@withTimeoutOrNull firstConnectionEstablished.await()
        } != null
    }

    /**
     * Connects an SSE session to this agent's MCP server
     */
    suspend fun connectSseSession(session: ServerSSESession) {
        val transport = SseServerTransport(
            endpoint = "", // href(ResourcesFormat(), Mcp.Msg(secret)),
            session = session
        )

        sseTransports.add(transport)
        if (!firstConnectionEstablished.isCompleted) {
            firstConnectionEstablished.complete(transport)
            this.session.events.emit(SessionEvent.AgentConnected(name))
        }

        handleBlocking()
        connect(transport)
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
     */
    suspend fun waitForMessage(
        filters: Set<SessionThreadMessageFilter> = setOf(),
        timeoutMs: Long = config.localSession.defaultWaitTimeout
    ): SessionThreadMessage? {
        return withTimeoutOrNull(timeoutMs) {
            val waiter = SessionAgentWaiter(filters)
            waiters.add(waiter)

            session.events.emit(SessionEvent.AgentWaitStart(name, filters))
            val msg = waiter.deferred.await()
            session.events.emit(SessionEvent.AgentWaitStop(name, msg))

            msg
        }
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
            tool.execute(this, request.arguments)
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
     * Called when a message was posted to a thread that mentioned this agent.
     */
    suspend fun notifyMessage(message: SessionThreadMessage) {
        waiters.forEach { it.tryMessage(message) }
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
    suspend fun asJsonState(): JsonObject =
        buildJsonObject {
            put("agentName", name)
            put("agentDescription", description)
            put("agentWaiting", waiters.isNotEmpty())
            put("agentConnected", firstConnectionEstablished.isCompleted)
        }

    /**
     * Returns the current state of this agent.  Used by the session API.
     */
    suspend fun getState(): SessionAgentState =
        SessionAgentState(
            name = name,
            registryAgentIdentifier = graphAgent.registryAgent.identifier,
            isWaiting = waiters.isNotEmpty(),
            isConnected = firstConnectionEstablished.isCompleted,
            description = description,
            links = links.map { it.name }.toSet()
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
        
        ```json
        [${agents.joinToString(",")}]
        ```
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
        You are an agent named $name.  The current UNIX time is ${System.currentTimeMillis()}.
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