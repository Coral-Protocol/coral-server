package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentRegistry
import org.coralprotocol.coralserver.payment.PaymentSessionId

@Serializable
@Description("A paid request for an agent.  GraphAgentRequest -> GraphAgent")
data class PaidGraphAgentRequest(
    val graphAgentRequest: GraphAgentRequest,
    val paidSessionId: PaymentSessionId
) {
    /**
     * Given a reference to the agent registry [AgentRegistry], this function will attempt to convert this request into
     * a [GraphAgent].  If [isRemote] is true, this function will ensure the [provider] is [GraphAgentProvider.Local]
     * and the [GraphAgentProvider.Local.runtime] is exported in the registry.
     *
     * @throws IllegalArgumentException if the agent registry cannot be resolved.
     */
    fun toGraphAgent(registry: AgentRegistry, isRemote: Boolean = false): GraphAgent = graphAgentRequest.toGraphAgent(registry, isRemote)

}