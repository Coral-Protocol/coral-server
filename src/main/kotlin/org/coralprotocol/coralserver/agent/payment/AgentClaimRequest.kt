package org.coralprotocol.coralserver.agent.payment

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.session.BUDGET_CLAIM_DESCRIPTION_MAX_LENGTH

@Serializable
data class AgentClaimRequest(
    @Description("The claim amount, specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.")
    val amount: AgentBudgetUnit,

    @Description("A description of the claim, must be at most $BUDGET_CLAIM_DESCRIPTION_MAX_LENGTH characters long")
    val description: String,

    @Description("Set this to false if the agent wants to handle budget exhaustion itself. Budget settings may kill agents on budget exhaustion even if this is set to false")
    val autoKill: Boolean = true
)