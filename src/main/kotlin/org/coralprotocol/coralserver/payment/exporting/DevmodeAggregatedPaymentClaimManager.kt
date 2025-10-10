package org.coralprotocol.coralserver.payment.exporting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.payment.toUsd
import org.coralprotocol.coralserver.payment.JupiterService
import org.coralprotocol.coralserver.payment.PaymentSessionId
private val logger = KotlinLogging.logger { }

class DevmodeAggregatedPaymentClaimManager(
    jupiterService: JupiterService
) : AggregatedPaymentClaimManager(jupiterService) {
    override suspend fun submitAggregatedClaim(
        claimAggregation: PaymentClaimAggregation,
        paymentSessionId: PaymentSessionId
    ) {
        val claimUsd = AgentClaimAmount.MicroCoral(claimAggregation.sumOfAllAgentsClaims()).toUsd(jupiterService)
        logger.info {
            "[DEVMODE] Claim submitted for session $paymentSessionId, amount claimed: ${usdFormat.format(claimUsd)}"
        }
    }
}