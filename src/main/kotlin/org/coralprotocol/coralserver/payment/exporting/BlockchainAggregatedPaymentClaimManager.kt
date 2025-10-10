package org.coralprotocol.coralserver.payment.exporting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.payment.toUsd
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.PaymentSessionId
import org.coralprotocol.payment.blockchain.BlockchainService

private val logger = KotlinLogging.logger { }

class BlockchainAggregatedPaymentClaimManager(
    val blockchainService: BlockchainService,
    jupiterService: JupiterService
) :
    AggregatedPaymentClaimManager(jupiterService) {
    override suspend fun submitAggregatedClaim(
        claimAggregation: PaymentClaimAggregation,
        paymentSessionId: PaymentSessionId
    ) {
        blockchainService.submitClaimMultiple(
            sessionId = paymentSessionId,
            claims = claimAggregation.toClaims(),
            authorityPubKey = claimAggregation.remoteSession.clientWalletAddress
        ).fold(
            onSuccess = {
                val claimUsd = AgentClaimAmount.MicroCoral(it.totalAmountClaimed).toUsd(jupiterService)
                logger.info {
                    "Claim submitted for session $paymentSessionId, amount claimed: ${usdFormat.format(claimUsd)}"
                }
            },
            onFailure = {
                val claimUsd =
                    AgentClaimAmount.MicroCoral(claimAggregation.sumOfAllAgentsClaims()).toUsd(jupiterService)
                logger.error(it) { "Escrow claim failed for $paymentSessionId, amount: ${usdFormat.format(claimUsd)}" }
            }
        )
    }
}