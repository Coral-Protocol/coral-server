package org.coralprotocol.coralserver.session
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.payment.PaymentSessionId
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.remote.RemoteSession
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

/**
 * This is the representation of a (local) Coral session.  Starting a session on a Coral server can only be done by
 * sending POST request to [Sessions].  A local session may contain imported agents that run on other Coral servers,
 * those agents do not have any special representation in the Local session, but on the remote server the agents ran are
 * part of a [RemoteSession].
 *
 * This class is not responsible for orchestrating agents, but it is responsible for handling any session data that the
 * agents have access to, including threads and messages.
 *
 * All agent states in this session are represented by [SessionAgent] classes listed in [agents].  A [SessionAgent]
 * instance contains all runtime information for that agent, including its MCP server [CoralAgentIndividualMcp].
 *
 * @param id This is a unique identifier for this session.  This should be cryptographically secure as it is used to
 * uniquely identify a session in a potentially multi-tenant environment.
 *
 * @param paymentSessionId This the payment session created by coral-escrow.  This will be null if there are no paid
 * agents in [agentGraph].
 *
 * @param agentGraph Each agent in [AgentGraph.agents] will create a [SessionAgent].  Each group in [AgentGraph.groups]
 * will connect the [SessionAgent]s and all tools specified in [AgentGraph.customTools] will be made available to
 * the agents in this session.
 */
class LocalSession(
    override val id: SessionId,
    override val paymentSessionId: PaymentSessionId? = null,
    namespace: LocalSessionNamespace,
    agentGraph: AgentGraph,
    sessionManager: LocalSessionManager
): Session(sessionManager.managementScope) {
    /**
     * Agent states in this session.  Note that even though one [SessionAgent] maps to one graph agent, the agent
     * that is orchestrated is not guaranteed to be connected to the [SessionAgent].  There will always be a slight
     * delay between orchestration and an MCP connection between the agent and the agent state.
     */
    val agents: Map<UniqueAgentName, SessionAgent> = agentGraph.agents.mapValues {
        SessionAgent(this, it.value, namespace, sessionManager)
    }

    /**
     * A list of threads in this session.  Threads are created by agents all messages in a session belong to threads.
     */
    val threads: ConcurrentHashMap<ThreadId, SessionThread> = ConcurrentHashMap()

    // Create links between agents from the groups in the agent graph
    init {
        for (group in agentGraph.groups) {
            val agentsInGroup = group.mapNotNull { agents[it] }
            val agentPairs = agentsInGroup
                .flatMap {agent ->
                    agentsInGroup
                        .filter { it != agent }
                        .map { agent to it }
                }

            agentPairs.forEach { (a, b) -> a.links.add(b) }
        }
    }

    /**
     * Agent jobs associated with this session.  Populated by [launchAgents].
     */
    private val agentJobs = mutableListOf<Job>()

    /**
     * Creates a new thread in this session.  The thread will start in an open state.
     *
     * @param threadName The name/title of the thread.  This title is visible to agents and should be used to describe
     * the purpose of this thread to agents in the session.
     * @param agentName The name of the agent that created this thread.  This agent will be added to the participants
     * of the thread.
     * @param participants The initial set of the names of the participants in this thread.  It is not necessary to add
     * the creator of the thread to this list.
     *
     * @throws SessionException.MissingAgentException if [agentName] does not exist in [agents]
     * @throws SessionException.MissingAgentException if any of the agents listed in [participants] do not exist in [agents]
     */
    fun createThread(
        threadName: String,
        agentName: UniqueAgentName,
        participants: Set<UniqueAgentName> = setOf()
    ): SessionThread {
        if (!agents.containsKey(agentName))
            throw SessionException.MissingAgentException("No agent named $agentName")

        val missing = participants.filter { !agents.containsKey(it) }
        if (missing.isNotEmpty())
            throw SessionException.MissingAgentException("No agents named ${missing.joinToString(", ")}}")

        // The creator of a thread should be a participant of a thread always.  This function is called by MCP tools,
        // and the result is given to agents. Agents tend to assume that they have access to threads they create.
        val thread = SessionThread(
            name = threadName,
            creatorName = agentName,
            participants = (participants + setOf(agentName)).toMutableSet(),
        )

        threads[thread.id] = thread
        return thread
    }

    /**
     * Returns a thread by its ID.
     * @throws SessionException.MissingThreadException if no thread exists in [threads] with the given ID.
     */
    fun getThreadById(threadId: ThreadId): SessionThread =
        threads[threadId] ?: throw SessionException.MissingThreadException("Thread with ID \"$threadId\" does not exist")

    /**
     * Returns an agent by its name.
     * @throws SessionException.MissingAgentException if no agent exists in [agents] with the given name.
     */
    fun getAgent(agentName: UniqueAgentName): SessionAgent =
        agents[agentName] ?: throw SessionException.MissingAgentException("No agent named $agentName")

    @TestOnly
    fun hasLink(agentName1: UniqueAgentName, agentName2: UniqueAgentName): Boolean =
        agents[agentName1]?.links?.contains(agents[agentName2]) ?: false

    /**
     * Launches all agents in this session on new coroutines, returning a list of jobs for each agent.  This function
     * can only be called once per session.
     *
     * @throws SessionException.AlreadyLaunchedException if this session's agents have already been launched
     */
    fun launchAgents() {
        if (agentJobs.isNotEmpty())
            throw SessionException.AlreadyLaunchedException("This session's agents have already been launched")

        agentJobs.addAll(agents.values.map { it.launch() })
    }

    /**
     * Waits for all agents in [agentJobs] to finish.  [launchAgents] must have been called before this function is
     * called.
     *
     * @throws SessionException.NotLaunchedException if [launchAgents] has not been called yet.
     */
    suspend fun waitForAgents() {
        if (agentJobs.isEmpty())
            throw SessionException.NotLaunchedException("This session's agents have not been launched yet")

        println("Waiting for agents to finish... " + agentJobs.size)
        agentJobs.joinAll()
        println("all agents finished")
    }
}