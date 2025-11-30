package org.coralprotocol.coralserver.session

import io.ktor.server.application.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.SseServerTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.logging.LoggerWithFlow
import org.coralprotocol.coralserver.mcp.addMcpTool
import org.coralprotocol.coralserver.mcp.tools.*
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
    sessionManager: LocalSessionManager
): Server(
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

    init {
        addMcpTool(AddParticipantTool())
        addMcpTool(CloseThreadTool())
        addMcpTool(CreateThreadTool())
        addMcpTool(ListAgentsTool())
        addMcpTool(RemoveParticipantTool())
        addMcpTool(SendMessageTool())
        addMcpTool(WaitForMentionsTool())

        // todo: custom tools
        // todo: plugins
    }

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
    var description = graphAgent.description ?: graphAgent.registryAgent.info.description

    /**
     * Connections to other agents.  These connections should be built using groups specified in [AgentGraph.groups]
     */
    val links = mutableSetOf<SessionAgent>()

    /**
     * A mutable list of channels.  Every channel in this list will receive messages that are posted to threads that
     * this agent participates in.  [Channel.trySend] is used to write messages.
     */
    private val messageChannels = mutableListOf<Channel<SessionThreadMessage>>()

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
        if (!firstConnectionEstablished.isCompleted)
            firstConnectionEstablished.complete(transport)

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
    fun sendMessage(
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

        val message= thread.addMessage(message, this, mentions)

        // notify participating agents
        thread.participants.forEach {
            session.getAgent(it).notifyMessage(message)
        }

        return message
    }

    /**
     * Suspends until this agent receives a message that matches all specified [filters] or until [timeout] is reached.
     *
     * Note that under normal conditions this would only be called once per agent, but for testing / debugging purposes
     * multiple channels are used to support multiple ongoing waits.
     */
    suspend fun waitForMessage(
        filters: Set<SessionThreadMessageFilter> = setOf(),
        timeout: Long = 60_000
    ): SessionThreadMessage? {
        val messageChannel = Channel<SessionThreadMessage>(Channel.CONFLATED)
        messageChannels.add(messageChannel)

        try {
            return withTimeoutOrNull(timeout) {
                for (message in messageChannel) {
                    if (filters.all { it.matches(message) }) {
                        return@withTimeoutOrNull message
                    }
                }

                return@withTimeoutOrNull null
            }
        }
        finally {
            messageChannels.remove(messageChannel)
        }
    }

    /**
     * Called when a message was posted to a thread that mentioned this agent.
     */
    fun notifyMessage(message: SessionThreadMessage) {
        messageChannels.forEach { it.trySend(message) }
    }

    /**
     * Returns a list of all threads that this agent is currently participating in.
     */
    fun getThreads() =
        session.threads.values.filter {
            it.participants.contains(graphAgent.name)
        }

    /**
     * Returns a list of all messages that this agent can see (from threads that it is participating in)
     */
    fun getVisibleMessages() =
        getThreads().flatMap { it.messages }

    /**
     * Launches this agent via [executionContext].
     */
    fun launch() = coroutineScope.launch {
        executionContext.launch()
    }

    override fun toString(): String {
        return "SessionAgentState(graphAgent=${name}, links=${links.joinToString(", ") { it.name }})"
    }
}