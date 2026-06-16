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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
        val force: Boolean = false,

        @Description("If force killing is enabled, it will be delayed by this amount before the agent is killed.  If this delay is too low the agent may be killed before it handles to response to a claim")
        @Optional
        val forceDelay: Duration = 100.milliseconds
    ) : SessionBudgetExhaustionBehavior

    @Serializable
    @SerialName("kill_session")
    @Description("Once the session budget drops below the specified minimum, agents that claim for it will trigger the session to be killed.  The higher the minimum is the lower the chance of overclaiming.")
    data class KillSession(
        @Description("The minimum value, specified in micro cents. $1.00 is $MICRO_CENTS_TO_DOLLARS and $0.01 is $MICRO_CENTS_TO_CENTS.")
        val minimum: AgentBudgetUnit = AgentBudgetUnit(),

        @Description("The delay before the session is killed.  If this delay is too low, the agent whose claim triggered this may be killed before it receives the response to that claim")
        @Optional
        val delay: Duration = 100.milliseconds
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
    val budget: AgentBudgetUnit = AgentBudgetUnit.ZERO,

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