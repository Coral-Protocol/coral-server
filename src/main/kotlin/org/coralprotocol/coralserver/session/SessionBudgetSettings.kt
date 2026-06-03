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
sealed interface SessionBudgetExhaustionBehavior {
    @Serializable
    @SerialName("warn")
    @Description("Once the session budget is exhausted and claimed from, a warning will be produced.  This behavior has a high risk of overclaiming.")
    object Warn : SessionBudgetExhaustionBehavior

    @Serializable
    @SerialName("kill_agent")
    @Description("Once the session budget drops below the specified minimum, agents that claim for it will be killed.  The higher the minimum is the lower the chance of overclaiming.")
    data class KillAgent(
        @Description("The minimum value, specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.")
        val minimum: AgentBudgetUnit = AgentBudgetUnit(),

        @Description("If this is true, when an agent claims from the session budget that is below the minimum, the agent will be killed immediately.  If this is false, the agent will only be killed if the agent requests for automatic closing.")
        val force: Boolean = false
    ) : SessionBudgetExhaustionBehavior

    @Serializable
    @SerialName("kill_session")
    @Description("Once the session budget drops below the specified minimum, agents that claim for it will trigger the session to be killed.  The higher the minimum is the lower the chance of overclaiming.")
    data class KillSession(
        @Description("The minimum value, specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.")
        val minimum: AgentBudgetUnit = AgentBudgetUnit(),
    ) : SessionBudgetExhaustionBehavior
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
    val budget: AgentBudgetUnit = AgentBudgetUnit(),

    @Description(
        """
        The behavior for agents consuming from this budget after it has been exhausted.  Note that this behavior
        only applies to agents that consume from the session budgets. Agents that have their own budget will first 
        perform behaviors described by the agent's own budget settings.
        """
    )
    @Optional
    val exhaustionBehavior: SessionBudgetExhaustionBehavior = SessionBudgetExhaustionBehavior.KillAgent(),
)