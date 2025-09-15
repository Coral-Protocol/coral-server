package org.coralprotocol.coralserver.agent.payment

import org.coralprotocol.coralserver.agent.graph.GraphAgent

data class AgentGraphPayment(
    val paidAgents: List<GraphAgent>
) {

}
