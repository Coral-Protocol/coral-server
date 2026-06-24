@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_CENTS
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_DOLLARS
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Serializable
@JsonClassDiscriminator("type")
sealed interface AgentBudgetExhaustionBehavior {
    @Serializable
    @SerialName("kill")
    @Description("Once the agent's budget is less than the specified minimum amount, the agent will be killed.  The higher the minimum is the lower the chance of overclaiming. This behavior will stop the agent claiming from the session's budget")
    data class Kill(
        @Description("The minimum value, specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.")
        val minimum: AgentBudgetUnit = AgentBudgetUnit(),

        @Description("If this is true, the agent will be killed immediately.  If this is false, the agent will only be killed if the claim requests for automatic closing.")
        val force: Boolean = false,

        @Description("If force killing is enabled, it will be delayed by this amount before the agent is killed.  If this delay is too low the agent may be killed before it handles to response to a claim")
        @Optional
        val forceDelay: Duration = 100.milliseconds
    ) : AgentBudgetExhaustionBehavior

    @Serializable
    @SerialName("consume_session")
    @Description("Once's the agent's budget is exhausted, it will consume the session's budget.  If the session's budget is also exhausted, the session's exhaustion behavior will be applied.  If a claim is made that cannot be fully fulfilled by the agent's budget, the remainder will be taken from the session's budget.")
    object ConsumeSession : AgentBudgetExhaustionBehavior
}

@Serializable
data class GraphAgentBudgetSettings(
    @Description("The budget for this specific agent.  The value is specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.")
    @Optional
    val budget: AgentBudgetUnit = AgentBudgetUnit.ZERO,

    @Description("A map where each key is a claim type name and the value the cost for that claim. Claims that do not have default values set must have their values set in this map or else they will not contribute to the budget.")
    @Optional
    val claimTypeCosts : Map<String, AgentBudgetUnit> = emptyMap(),

    @Description("The behavior of agent budget exhaustion, defaults to consuming session budget.")
    @Optional
    val exhaustionBehavior: AgentBudgetExhaustionBehavior = AgentBudgetExhaustionBehavior.ConsumeSession,
)
