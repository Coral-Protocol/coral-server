package org.coralprotocol.coralserver.agent.payment

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class AgentPaymentClaimRequest(
    @Description("The amount to claim.  This will be converted to Coral when received")
    val amount: AgentClaimAmount
)