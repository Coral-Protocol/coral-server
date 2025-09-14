package org.coralprotocol.coralserver.payment.exporting

import io.github.oshai.kotlinlogging.KotlinLogging
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


private class PaymentClaimAggregation(val maxCost: Long) {
    val involvedAgents: MutableSet<String> = mutableSetOf()
    private val claimList: MutableList<AgentPaymentClaimRequest> = mutableListOf()
    var totalCoralClaimed: Long = 0
        private set

    fun getRemainingBudget(): Long = maxCost - totalCoralClaimed

    suspend fun addClaim(
        claim: AgentPaymentClaimRequest,
        agentId: AgentRegistryIdentifier,
        jupiterService: JupiterService
    ): Long {
        claimList.add(claim)
        involvedAgents.add(agentId.toString())
        val newTotal = claimList.sumOf { it.amount.toMicroCoral(jupiterService) }
        totalCoralClaimed = newTotal
        return newTotal
    }
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
        val paymentSessionId =
            session.paymentSessionId ?: throw IllegalArgumentException("Payment session does not contain paid agents")

        val maxCost = session.maxCost

        val aggregation = claimMap.getOrPut(paymentSessionId) {
            PaymentClaimAggregation(maxCost)
        }
        aggregation.addClaim(claim, session.agent.registryAgent.info.identifier, jupiterService)

        val claimUsd = claim.amount.toUsd(jupiterService)
        val remainingUsd = jupiterService.coralToUsd(aggregation.getRemainingBudget().toDouble())

        logger.info { "${session.agent.name} claimed ${usdFormat.format(claimUsd)} for session $paymentSessionId, amount remaining: ${usdFormat.format(remainingUsd)}" }

        return aggregation.getRemainingBudget()
    }

    suspend fun notifyPaymentSessionCosed(paymentSessionId: PaymentSessionId) {
        val claimAggregation = claimMap[paymentSessionId]
        if (claimAggregation == null) {
            logger.warn { "Remote session $paymentSessionId ended with no claims" }
            return
        }

        blockchainService.submitEscrowClaim(
            sessionId = paymentSessionId,
            agentId = claimAggregation.involvedAgents.joinToString(", "),
            amount = claimAggregation.totalCoralClaimed
        ).fold(
            onSuccess = {
                val claimUsd = jupiterService.coralToUsd(it.amountClaimed.toDouble())
                val remainingUsd = jupiterService.coralToUsd(it.remainingInSession.toDouble())

                logger.info { "Claim submitted for session $paymentSessionId, amount claimed: ${usdFormat.format(claimUsd)}, amount remaining: ${usdFormat.format(remainingUsd)}" }
            },
            onFailure = {
                val claimUsd = jupiterService.coralToUsd(claimAggregation.totalCoralClaimed.toDouble())
                logger.error(it) { "Escrow claim failed for $paymentSessionId, amount: ${usdFormat.format(claimUsd)}" }
            }
        )
    }
}