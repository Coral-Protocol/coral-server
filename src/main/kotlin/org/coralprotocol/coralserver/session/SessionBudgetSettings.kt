@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_CENTS
import org.coralprotocol.coralserver.agent.payment.MICRO_CENTS_TO_DOLLARS

@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionBudgetExhaustionBehaviour {
    @Serializable
    @SerialName("warn")
    @Description("Once the session budget is exhausted and claimed from, a warning will be produced.  This behaviour has a high risk of overclaiming.")
    object Warn : SessionBudgetExhaustionBehaviour

    @Serializable
    @SerialName("exit")
    @Description(
        """
        Once the session budget drops below the specified minimum, the session will close.  The higher the minimum is the lower the chance of overclaiming.
        
        The minimum value is specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.")
        """
    )
    data class Exit(val minimum: AgentBudgetUnit = AgentBudgetUnit(0)) : SessionBudgetExhaustionBehaviour
}

@Serializable
@Description("Budgets settings for this session.  Budgets are consumed by agents in the session.")
data class SessionBudgetSettings(
    @Description(
        """
        This budget is shared across all agents in the session and can be used by any agent configured to consume the shared budget.
        
        The value is specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.")    
        """
    )
    @Optional
    val budget: AgentBudgetUnit = AgentBudgetUnit(0),

    @Description(
        """
        The behaviour for agents consuming from this budget after it has been exhausted.  Note that this behaviour
        only applies to agents that consume from the session budgets. Agents that have their own budget will first 
        perform behaviours described by the agent's own budget settings.
        """
    )
    @Optional
    val exhaustionBehaviour: SessionBudgetExhaustionBehaviour = SessionBudgetExhaustionBehaviour.Exit(),
)