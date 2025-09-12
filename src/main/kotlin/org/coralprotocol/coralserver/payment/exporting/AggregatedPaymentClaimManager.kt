package org.coralprotocol.coralserver.payment.exporting

import io.github.oshai.kotlinlogging.KotlinLogging
import org.coralprotocol.coralserver.agent.payment.AgentPaymentClaimRequest
import org.coralprotocol.coralserver.agent.payment.toCoral
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.payment.PaymentSessionId
import org.coralprotocol.coralserver.session.remote.RemoteSession
import org.coralprotocol.payment.blockchain.BlockchainService

private val logger = KotlinLogging.logger { }


private class PaymentClaimAggregation(val maxCost: Long) {
    val involvedAgents: MutableSet<String> = mutableSetOf()
    private val claimList: MutableList<AgentPaymentClaimRequest> = mutableListOf()
    var totalClaimed: Long = 0L
        private set

    fun getRemainingBudget(): Long = maxCost - totalClaimed

    fun addClaim(claim: AgentPaymentClaimRequest, agentId: AgentRegistryIdentifier): Long {
        claimList.add(claim)
        involvedAgents.add(agentId.toString())
        val newTotal = claimList.sumOf { it.amount.toCoral() }
        totalClaimed = newTotal
        return newTotal
    }
}


class AggregatedPaymentClaimManager(val blockchainService: BlockchainService) {
    private val claimMap = mutableMapOf<PaymentSessionId, PaymentClaimAggregation>()

    /**
     * Called multiple times from one agent, probably called per "work" item
     * @return [Long] Remaining budget for this session
     */
    fun addClaim(claim: AgentPaymentClaimRequest, session: RemoteSession): Long {
        val paymentSessionId =
            session.paymentSessionId ?: throw IllegalArgumentException("Payment session does not contain paid agents")

        val maxCost = session.maxCost

        val aggregation = claimMap.getOrPut(paymentSessionId) {
            PaymentClaimAggregation(maxCost)
        }
        aggregation.addClaim(claim, session.agent.registryAgent.info.identifier)

        logger.info { "${session.agent.name} claimed ${claim.amount} for session $paymentSessionId, amount remaining: ${aggregation.getRemainingBudget()}" }

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
            amount = claimAggregation.totalClaimed
        ).fold(
            onSuccess = {
                logger   .info { "Claim submitted for session $paymentSessionId, amount claimed: ${it.amountClaimed}, amount remaining: ${it.remainingInSession}" }
            },
            onFailure = {
                logger.error(it) { "Escrow claim failed for $paymentSessionId, amount: ${claimAggregation.totalClaimed}" }
            }
        )
    }
}