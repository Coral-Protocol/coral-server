package org.coralprotocol.coralserver.payment.exporting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.payment.AgentPaymentClaimRequest
import org.coralprotocol.coralserver.agent.payment.toMicroCoral
import org.coralprotocol.coralserver.agent.payment.toUsd
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.PaymentSessionId
import org.coralprotocol.coralserver.session.remote.RemoteSession
import java.text.NumberFormat
import java.util.*

private val logger = KotlinLogging.logger { }

internal class PaymentClaimAggregation(val remoteSession: RemoteSession) {
    private val totalClaimed: MutableMap<String, Long> = mutableMapOf()

    fun sumOfAllAgentsClaims(): Long = totalClaimed.values.sum()
    fun getRemainingBudget(): Long = remoteSession.maxCost - totalClaimed.values.sum()

    suspend fun addClaim(
        claim: AgentPaymentClaimRequest,
        agentId: String,
        jupiterService: JupiterService
    ) {
        val requestNewAmount = totalClaimed.getOrDefault(agentId, 0L) +
                claim.amount.toMicroCoral(jupiterService)

        totalClaimed[agentId] = if (requestNewAmount > remoteSession.maxCost) {
            logger.warn { "maxCost for ${remoteSession.agent.name} reached!" }
            logger.warn { "clipping excess of ${requestNewAmount - remoteSession.maxCost}" }
            remoteSession.maxCost
        } else {
            requestNewAmount
        }
    }

    fun toClaims(): List<Pair<String, Long>> =
        totalClaimed.toList()
}

interface PaymentClaimManager {
    /**
     * Called multiple times from one agent, probably called per "work" item
     * @return [Long] Remaining budget for this session
     */
    suspend fun addClaim(claim: AgentPaymentClaimRequest, session: RemoteSession): Long

    suspend fun notifyPaymentSessionCosed(paymentSessionId: PaymentSessionId)
}

abstract class AggregatedPaymentClaimManager(
    val jupiterService: JupiterService
) : PaymentClaimManager {
    private val claimMap = mutableMapOf<PaymentSessionId, PaymentClaimAggregation>()
    internal val usdFormat = NumberFormat.getCurrencyInstance(Locale.US)

    /**
     * Called multiple times from one agent, probably called per "work" item
     * @return [Long] Remaining budget for this session
     */
    override suspend fun addClaim(claim: AgentPaymentClaimRequest, session: RemoteSession): Long {
        val paymentSessionId = session.paymentSessionId

        val aggregation = claimMap.getOrPut(paymentSessionId) {
            PaymentClaimAggregation(session)
        }
        aggregation.addClaim(claim, session.agent.name, jupiterService)

        val claimUsd = claim.amount.toUsd(jupiterService)
        val remainingUsd = AgentClaimAmount.MicroCoral(aggregation.getRemainingBudget()).toUsd(jupiterService)

        logger.info {
            "${session.agent.name} claimed ${usdFormat.format(claimUsd)} for session $paymentSessionId, amount remaining: ${
                usdFormat.format(remainingUsd)
            }"
        }

        return aggregation.getRemainingBudget()
    }

    internal abstract suspend fun submitAggregatedClaim(
        paymentClaimAggregation: PaymentClaimAggregation,
        paymentSessionId: PaymentSessionId
    )

    override suspend fun notifyPaymentSessionCosed(paymentSessionId: PaymentSessionId) {
        val claimAggregation = claimMap[paymentSessionId]
        if (claimAggregation == null) {
            logger.warn { "Remote session $paymentSessionId ended with no claims" }
            return
        }
        submitAggregatedClaim(claimAggregation, paymentSessionId)
    }
}

