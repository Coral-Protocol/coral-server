package org.coralprotocol.coralserver.x402

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
@Description("A budget given to an agent for a specific resource")
data class X402BudgetedResource(
    @Description("The priority of this budget.  If a x402 service accepts multiple resources and an agent was given a budget for multiple resources, the resource with the highest priority will be used until it is consumed")
    val priority: Int,

    @Description("The this budget is for")
    val resource: String,

    @Description("The remaining budget for this resource in atomic units")
    var remainingBudget: ULong
)
