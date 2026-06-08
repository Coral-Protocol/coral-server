package org.coralprotocol.coralserver.session

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.util.InstantSerializer
import org.coralprotocol.coralserver.util.utcTimeNow
import kotlin.time.Instant

const val BUDGET_CLAIM_DESCRIPTION_MAX_LENGTH = 256

@Serializable
data class SessionBudgetClaim(
    val amount: AgentBudgetUnit,
    val description: String,

    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = utcTimeNow()
)

@Serializable
data class SessionRunningBudget(
    val startBudget: AgentBudgetUnit,
    val clamp: Boolean,
) {
    var remaining = startBudget
    var overclaim = AgentBudgetUnit()
    val claims: MutableList<SessionBudgetClaim> = mutableListOf()

    fun addClaim(amount: AgentBudgetUnit, description: String): AgentBudgetUnit {
        val maxClaim = amount.coerceAtMost(remaining)
        claims.add(
            SessionBudgetClaim(
                if (clamp) {
                    maxClaim
                } else {
                    amount
                }, description
            )
        )

        return if (amount > remaining) {
            if (clamp) {
                remaining -= maxClaim
                maxClaim
            } else {
                remaining -= maxClaim
                overclaim += (amount - maxClaim)
                amount
            }
        } else {
            remaining -= amount
            amount
        }
    }
}
