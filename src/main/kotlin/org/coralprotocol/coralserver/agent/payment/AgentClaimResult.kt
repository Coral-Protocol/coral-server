package org.coralprotocol.coralserver.agent.payment

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable


@Serializable
@Description("A response to a claim request.")
data class AgentClaimResult(
    @Description("A unique sequential ID assigned to this claim")
    val claimId: Int,

    @Description("The amount requested, echoed back to the agent")
    val requestedAmount: AgentBudgetUnit,

    @Description("The amount of the budget that could be fulfilled. If this is not equal to the requested amount, the budget was exhausted.")
    val fulfilledAmount: AgentBudgetUnit,

    @Description("The remaining agent budget after the claim was made")
    val remainingAgentBudget: AgentBudgetUnit,

    @Description("The remaining session budget after the claim was made")
    val remainingSessionBudget: AgentBudgetUnit,

    @Description("If this is true, the claim resulted in a budget exhaustion that should cause the agent to exit.")
    val shouldExit: Boolean,
)