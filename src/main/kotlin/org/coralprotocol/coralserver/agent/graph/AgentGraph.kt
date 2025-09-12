package org.coralprotocol.coralserver.agent.graph

import org.coralprotocol.coralserver.agent.payment.AgentGraphPayment
import org.coralprotocol.coralserver.session.CustomTool

/**
 * @see AgentGraphRequest
 */
data class AgentGraph(
    /**
     * @see AgentGraphRequest.agents
     */
    val agents: Map<String, GraphAgent>,

    /**
     * @see AgentGraphRequest.customTools
     */
    val customTools: Map<String, CustomTool>,

    /**
     * @see AgentGraphRequest.groups
     */
    val groups: Set<Set<String>>,
) {
    fun toPayment(): AgentGraphPayment {
        return AgentGraphPayment(
            paidAgents = agents.values.filter {
                it.provider is GraphAgentProvider.RemoteRequest
            }.toList()
        )
    }
}