package org.coralprotocol.coralserver.agent.payment

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
@Description("""
    This object is returned by the agent claim endpoint and represents the remaining budget for the agent.  The agent 
    should use this respond to decide whether whether it can continue providing services with the remaining budget.
    
    This object attaches a USD price for a Coral to it so that agents do not have to make multiple calls to the pricing 
    endpoint to determine the price of a Coral.  This field is an 'estimate', it can be based off cached data and may not 
    be accurate, so this should only be used if the agent represented it's rates in USD.
    
    If better accuracy is required agent-side, the budget should only use micro-corals.
""")
data class AgentRemainingBudget(
    @Description("The remaining budget for the agent, represented in micro-corals")
    val remainingBudget: Long,

    @Description("Current USD price for one whole Coral")
    val coralUsdPrice: Double
)