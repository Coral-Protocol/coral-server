package org.coralprotocol.coralserver.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit

data class BudgetClaimResult(
    val fulfilled: AgentBudgetUnit = AgentBudgetUnit.ZERO,
    val overclaim: AgentBudgetUnit = AgentBudgetUnit.ZERO,
    val totalRemaining: AgentBudgetUnit = AgentBudgetUnit.ZERO,
    val totalOverclaim: AgentBudgetUnit = AgentBudgetUnit.ZERO,
)

@Serializable
data class SessionRunningBudget(
    val startBudget: AgentBudgetUnit,
    val clamp: Boolean,
) {
    var remaining = startBudget
    var overclaim = AgentBudgetUnit()

    @Transient
    private val mutex = Mutex()

    suspend fun addClaim(amount: AgentBudgetUnit): BudgetClaimResult {
        return mutex.withLock {
            val maxClaim = amount.coerceAtMost(remaining)

            if (amount > remaining) {
                if (clamp) {
                    remaining -= maxClaim
                    BudgetClaimResult(
                        fulfilled = maxClaim,
                        totalRemaining = remaining,
                        totalOverclaim = overclaim
                    )
                } else {
                    remaining -= maxClaim
                    overclaim += (amount - maxClaim)
                    BudgetClaimResult(
                        fulfilled = maxClaim,
                        overclaim = (amount - maxClaim),
                        totalOverclaim = overclaim
                    )
                }
            } else {
                remaining -= amount
                BudgetClaimResult(
                    fulfilled = maxClaim,
                    totalRemaining = remaining,
                    totalOverclaim = overclaim
                )
            }
        }
    }
}
