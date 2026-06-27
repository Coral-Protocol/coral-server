package org.coralprotocol.coralserver.agent.graph

/**
 * UniqueAgentName is the name given for an agent in [GraphAgentRequest.name].  Note that the [GraphAgentRequest]
 * cannot use this typealias due to serialization problems.
 */
typealias UniqueAgentName = String

/**
 * @see AgentGraphRequest
 */
data class AgentGraph(
    /**
     * @see AgentGraphRequest.agents
     */
    val agents: Map<UniqueAgentName, GraphAgent>,

    /**
     * @see AgentGraphRequest.customTools
     */
    val customTools: Map<UniqueAgentName, GraphAgentTool> = mapOf(),

    /**
     * @see AgentGraphRequest.groups
     */
    val groups: Set<Set<UniqueAgentName>> = setOf()
)