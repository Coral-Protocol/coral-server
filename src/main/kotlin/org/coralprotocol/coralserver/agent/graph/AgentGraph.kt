package org.coralprotocol.coralserver.agent.graph

import org.coralprotocol.coralserver.agent.payment.AgentGraphPayment
import org.coralprotocol.coralserver.session.CustomTool

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
    val customTools: Map<UniqueAgentName, CustomTool>,

    /**
     * @see AgentGraphRequest.groups
     */
    val groups: Set<Set<UniqueAgentName>>,
) {
    fun toPayment(): AgentGraphPayment {
        return AgentGraphPayment(
            paidAgents = agents.values.filter {
                it.provider is GraphAgentProvider.RemoteRequest
            }.toList()
        )
    }
}