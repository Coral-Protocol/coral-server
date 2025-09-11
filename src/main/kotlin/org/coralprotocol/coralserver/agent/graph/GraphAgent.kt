package org.coralprotocol.coralserver.agent.graph

import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.routes.api.v1.Sessions
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.remote.RemoteSession

/**
 * Coral agent modeling
 *
 * The life of an agent starts within the [AgentRegistry], the registry lists all [RegistryAgent]'s that are available
 * for the server to use.  No agent will ever exist in the server not defined in the registry.  The registry also lists
 * [AgentExportSettings]'s , these are agents that this server will provide as remote agents.  The [AgentExportSettings] type contains a
 * reference to the [RegistryAgent] that is to be exported and pricing information.  It is an invalid configuration to
 * export an agent that is not itself imported.
 *
 * Every agent in the registry is identified using [AgentRegistryIdentifier].  A registry is guaranteed to only have one agent
 * with a given identifier, it is an invalid configuration to have more than one agent with the same identifier.
 *
 * The use of agents in Coral server happens exclusively within sessions, either a [LocalSession] or a [RemoteSession].
 * To start a session, a POST request to [Sessions] must be made, the relevant member of the request body is a
 * [AgentGraphRequest] which is a request for a graph of agents, where each agent is in the graph is represented by a
 * [GraphAgentRequest].
 *
 * A successful and valid request will result in the creation of a [AgentGraph] that contains [GraphAgent]'s. To put it
 * simply: [AgentGraphRequest] / [GraphAgentRequest] => [AgentGraph] / [GraphAgent].
 *
 * The [AgentGraph] and [GraphAgent] types are runtime types and should not be serializable. Both [AgentGraphRequest]
 * and [GraphAgentRequest] are serializable types, because they must be sent via HTTP (JSON).  All registry types are
 * also serializable because they come from a config file (TOML)
 *
 * @see GraphAgentRequest
 */
data class GraphAgent(
    /**
     * The [RegistryAgent] that this agent represents.
     */
    val registryAgent: RegistryAgent,

    /**
     * @see GraphAgentRequest.name
     */
    val name: String,

    /**
     * @see GraphAgentRequest.description
     */
    val description: String?,

    /**
     * @see GraphAgentRequest.options
     */
    val options: Map<String, AgentOptionValue>,

    /**
     * @see GraphAgentRequest.systemPrompt
     */
    val systemPrompt: String?,

    /**
     * @see GraphAgentRequest.blocking
     */
    val blocking: Boolean?,

    /**
     * @see GraphAgentRequest.customToolAccess
     */
    val customToolAccess: Set<String>,

    /**
     * @see GraphAgentRequest.plugins
     */
    val plugins: Set<GraphAgentPlugin>,

    /**
     * @see GraphAgentRequest.provider
     */
    var provider: GraphAgentProvider,
)