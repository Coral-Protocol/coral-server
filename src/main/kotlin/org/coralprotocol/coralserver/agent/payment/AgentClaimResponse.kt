package org.coralprotocol.coralserver.agent.payment

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
@Description("A response to a claim request.")
data class AgentClaimResponse(
    @Description("The amount requested, echoed back to the agent")
    val requestedAmount: AgentBudgetUnit,

    @Description("The amount of the budget that could be fulfilled. If this is not equal to the requested amount, the budget was exhausted.")
    val fulfilledAmount: AgentBudgetUnit,

    @Description(
        """
        The remaining budget available to the agent. This includes the agent budget and the session budget, if the agent is configured to use it. 
        
        NOTE: It is possible that the budgets are configured to warn when empty, which will allow claims to exceed the defined budgets. It is possible that the remaining amount reaches zero but claiming is still possible.
        """
    )
    val remainingAmount: AgentBudgetUnit,

    @Description("If this is true, the claim resulted in a budget exhaustion that should cause the agent to exit.")
    val shouldExit: Boolean,
)