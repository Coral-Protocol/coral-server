package org.coralprotocol.coralserver.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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

data class SessionClaimResult(
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
    val claims: MutableList<SessionBudgetClaim> = mutableListOf()

    @Transient
    private val mutex = Mutex()

    suspend fun addClaim(amount: AgentBudgetUnit, description: String): SessionClaimResult {
        return mutex.withLock {
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

            if (amount > remaining) {
                if (clamp) {
                    remaining -= maxClaim
                    SessionClaimResult(
                        fulfilled = maxClaim,
                        totalRemaining = remaining,
                        totalOverclaim = overclaim
                    )
                } else {
                    remaining -= maxClaim
                    overclaim += (amount - maxClaim)
                    SessionClaimResult(
                        fulfilled = maxClaim,
                        overclaim = (amount - maxClaim),
                        totalOverclaim = overclaim
                    )
                }
            } else {
                remaining -= amount
                SessionClaimResult(
                    fulfilled = maxClaim,
                    totalRemaining = remaining,
                    totalOverclaim = overclaim
                )
            }
        }
    }
}
