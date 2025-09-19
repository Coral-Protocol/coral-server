package org.coralprotocol.coralserver.payment.exporting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.payment.AgentPaymentClaimRequest
import org.coralprotocol.coralserver.agent.payment.toMicroCoral
import org.coralprotocol.coralserver.agent.payment.toUsd
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.PaymentSessionId
import org.coralprotocol.coralserver.session.remote.RemoteSession
import org.coralprotocol.payment.blockchain.BlockchainService
import java.text.NumberFormat
import java.util.*

private val logger = KotlinLogging.logger { }


private class PaymentClaimAggregation(val remoteSession: RemoteSession) {
    private val totalClaimed: MutableMap<String, Long> = mutableMapOf()

    fun sumOfAllAgentsClaims(): Long = totalClaimed.values.sum()
    fun getRemainingBudget(): Long = remoteSession.maxCost - totalClaimed.values.sum()

    suspend fun addClaim(
        claim: AgentPaymentClaimRequest,
        agentId: String,
        jupiterService: JupiterService
    ) {
        totalClaimed[agentId] = totalClaimed.getOrDefault(agentId, 0L) +
                claim.amount.toMicroCoral(jupiterService)
    }

    fun toClaims(): List<Pair<String, Long>> =
        totalClaimed.toList()
}


class AggregatedPaymentClaimManager(
    val blockchainService: BlockchainService,
    val jupiterService: JupiterService
) {
    private val claimMap = mutableMapOf<PaymentSessionId, PaymentClaimAggregation>()
    private val usdFormat = NumberFormat.getCurrencyInstance(Locale.US)

    /**
     * Called multiple times from one agent, probably called per "work" item
     * @return [Long] Remaining budget for this session
     */
    suspend fun addClaim(claim: AgentPaymentClaimRequest, session: RemoteSession): Long {
        val paymentSessionId = session.paymentSessionId

        val aggregation = claimMap.getOrPut(paymentSessionId) {
            PaymentClaimAggregation(session)
        }
        aggregation.addClaim(claim, session.agent.name, jupiterService)

        val claimUsd = claim.amount.toUsd(jupiterService)
        val remainingUsd = AgentClaimAmount.MicroCoral(aggregation.getRemainingBudget()).toUsd(jupiterService)

        logger.info { "${session.agent.name} claimed ${usdFormat.format(claimUsd)} for session $paymentSessionId, amount remaining: ${usdFormat.format(remainingUsd)}" }

        return aggregation.getRemainingBudget()
    }

    suspend fun notifyPaymentSessionCosed(paymentSessionId: PaymentSessionId) {
        val claimAggregation = claimMap[paymentSessionId]
        if (claimAggregation == null) {
            logger.warn { "Remote session $paymentSessionId ended with no claims" }
            return
        }

        blockchainService.submitClaimMultiple(
            sessionId = paymentSessionId,
            claims = claimAggregation.toClaims(),
            authorityPubKey = claimAggregation.remoteSession.clientWalletAddress
        ).fold(
            onSuccess = {
                val claimUsd = AgentClaimAmount.MicroCoral(it.totalAmountClaimed).toUsd(jupiterService)
                logger.info { "Claim submitted for session $paymentSessionId, amount claimed: ${usdFormat.format(claimUsd)}" }
            },
            onFailure = {
                val claimUsd = AgentClaimAmount.MicroCoral(claimAggregation.sumOfAllAgentsClaims()).toUsd(jupiterService)
                logger.error(it) { "Escrow claim failed for $paymentSessionId, amount: ${usdFormat.format(claimUsd)}" }
            }
        )
    }
}