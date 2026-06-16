package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_CENTS
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_DOLLARS

@Serializable
data class RegistryAgentClaimType(
    @Description("A unique name for this claim type. This name will be used to make claims of this type")
    val name: String,

    @Description("A human-readable description of this claim type")
    val description: String,

    @SerialName("dependency")
    val dependencyName: String,

    @Description("The cost of this claim type. This value can be overridden by the application. Application developers are encouraged to specify values for all claim types. The value is specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.")
    @Optional
    val cost: AgentBudgetUnit? = null
)
