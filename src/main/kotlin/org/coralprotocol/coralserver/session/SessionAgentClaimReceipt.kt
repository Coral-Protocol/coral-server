package org.coralprotocol.coralserver.session

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit

@Serializable
@Description("A receipt for a claim made by an agent")
data class SessionAgentClaimReceipt(
    @Description("The claim that was made")
    val claim: SessionAgentClaim,

    @Description("The calculated cost of the claim")
    val cost: AgentBudgetUnit,

    @Description("A unique sequential ID assigned to this claim")
    val id: Int
)
