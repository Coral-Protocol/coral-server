//package org.coralprotocol.coralserver.payment.orchestration
//
//import io.github.oshai.kotlinlogging.KotlinLogging
//import kotlinx.coroutines.*
//import org.coralprotocol.coralserver.agent.payment.PaidAgent
//import org.coralprotocol.coralserver.payment.models.*
//import org.coralprotocol.coralserver.payment.utils.SessionIdUtils
//import org.coralprotocol.coralserver.session.payment.PaymentSession
//import org.coralprotocol.payment.blockchain.BlockchainService
//
//private val logger = KotlinLogging.logger {}
//
///**
// * Manages session creation with agent availability checking.
// * Implements the agent-first pattern where sessions are only created
// * after confirming agent availability.
// */
//class PaymentSessionManager(
//    private val blockchain: BlockchainService,
//    private val agentClient: AgentNotificationClient
//) {
//
//    //TODO: Move to importing server session post/ local session manager or something
//    /**
//     * Create and fund session atomically after checking all agents are available.
//     * Fails if ANY agent is unavailable.
//     */
//    suspend fun createAndFundSession(
//        agents: List<PaidAgent>,
//        mintPubkey: String,
//        sessionId: String? = null,
//        fundAmount: Long? = null
//    ): PaymentSession {
//        // Validate request
//        require(fundAmount != null && fundAmount > 0) {
//            "Fund amount must be specified and greater than 0"
//        }
//
//
//
//        // Pre-check wallet balance
//        val balanceResult = preCheckBalance(fundAmount)
//        if (balanceResult.isFailure) {
//            throw balanceResult.exceptionOrNull()!!
//        }
//
//        logger.info {
//            "Creating session with availability check: " +
//            "sessionId=$sessionIdLong, agents=${agents.size}"
//        }
//
//        // 1. Check agent availability (parallel)
//        val availabilityResult = checkAgentAvailability(
//            CheckAvailabilityRequest(
//                sessionId = sessionIdLong.toString(),
//                agents = agents,
//                minimumAgents = agents.size,  // ALL agents must be available
//                timeoutMs = 10000   // 10-second timeout
//            )
//        )
//
//        if (!availabilityResult.canProceed) {
//            throw InsufficientAgentsException(
//                "All agents must be available. " +
//                "Unavailable: ${availabilityResult.unavailableAgents}"
//            )
//        }
//
//        // 2. Create a session on a blockchain with all agents
//        val availableAgentConfigs = agents
//
//        logger.info {
//            "Creating session on blockchain with ${availableAgentConfigs.size} available agents"
//        }
//
//
//        val createResult = blockchain.createEscrowSession(
//            agents = availableAgentConfigs.map { it.toBlockchainModel() },
//            mintPubkey = mintPubkey,
//            sessionId = sessionIdLong
//        )
////
//        if (createResult.isFailure) {
//            throw SessionCreationException(
//                "Failed to create session: ${createResult.exceptionOrNull()?.message}"
//            )
//        }
//
//        val sessionInfo = createResult.getOrThrow()
//
//        // 3. Fund session immediately (atomic operation)
//        logger.info { "Funding session $sessionIdLong with ${fundAmount} tokens" }
//
//        val fundResult = blockchain.fundEscrowSession(
//            sessionId = sessionIdLong,
//            amount = fundAmount
//        )
//
//        if (fundResult.isFailure) {
//            // Attempt to cleanup the unfunded session
//            logger.error { "Failed to fund session, attempting cleanup: ${fundResult.exceptionOrNull()?.message}" }
//
//            try {
//                val refundResult = blockchain.refundEscrowLeftover(sessionIdLong, mintPubkey)
//                if (refundResult.isSuccess) {
//                    logger.info { "Successfully cleaned up unfunded session $sessionIdLong" }
//                }
//            } catch (e: Exception) {
//                logger.error { "Failed to cleanup unfunded session $sessionIdLong: ${e.message}" }
//                // TODO: Add to cleanup queue for later retry
//            }
//
//            throw SessionFundingException(
//                "Session created but funding failed. Session has been closed.",
//                fundResult.exceptionOrNull()
//            )
//        }
//
//        // 4. Notify agents that the session has been funded
//        notifyAgentsFunded(sessionIdLong, availableAgentConfigs)
//
//        return PaymentSession(
//            sessionId = sessionIdLong,
//            transactionSignature = fundResult.getOrThrow().signature,
//            status = "FUNDED",
//            fundingRequired = false,
//            availableAgents = availabilityResult.availableAgents
//        )
//    }
//
////    /**
////     * Check agent availability in parallel.
////     */
////    private suspend fun checkAgentAvailability(
////        request: CheckAvailabilityRequest
////    ): CheckAvailabilityResponse = coroutineScope {
////        logger.info {
////            "Checking availability for ${request.agents.size} agents, " +
////            "session=${request.sessionId}"
////        }
////
////        // Check each agent in parallel
////        val results = request.agents.map { agent ->
////            async {
////                try {
////                    if (agent.endpoint == null) {
////                        logger.warn { "Agent ${agent.id} has no endpoint configured" }
////                        AgentAvailabilityResult(
////                            agentId = agent.id,
////                            available = false,
////                            error = "No endpoint configured"
////                        )
////                    } else {
////                        withTimeout(request.timeoutMs) {
////                            val response = agentClient.checkAvailability(
////                                endpoint = agent.endpoint,
////                                request = AvailabilityCheckRequest(
////                                    sessionId = request.sessionId,
////                                    agentConfig = AgentConfigForAvailability(
////                                        id = agent.id,
////                                        maxCap = agent.cap
////                                    )
////                                )
////                            )
////                            AgentAvailabilityResult(
////                                agentId = agent.id,
////                                available = response.available,
////                                reason = response.reason
////                            )
////                        }
////                    }
////                } catch (e: Exception) {
////                    logger.warn { "Failed to check availability for agent ${agent.id}: ${e.message}" }
////                    AgentAvailabilityResult(
////                        agentId = agent.id,
////                        available = false,
////                        error = e.message
////                    )
////                }
////            }
////        }.awaitAll()
////
////        val availableAgents = results.filter { it.available }.map { it.agentId }
////        val unavailableAgents = results.filter { !it.available }.map { it.agentId }
////        val unreachableEndpoints = results
////            .filter { it.error != null && it.error.contains("timeout", ignoreCase = true) }
////            .mapNotNull { result ->
////                request.agents.find { it.id == result.agentId }?.endpoint
////            }
////            .distinct()
////
////        logger.info {
////            "Availability check complete: ${availableAgents.size} available, " +
////            "${unavailableAgents.size} unavailable"
////        }
////
////        CheckAvailabilityResponse(
////            sessionId = request.sessionId,
////            availableAgents = availableAgents,
////            unavailableAgents = unavailableAgents,
////            canProceed = availableAgents.size >= request.minimumAgents,
////            unreachableEndpoints = unreachableEndpoints
////        )
////    }
//
////    /**
////     * Notify agents that session has been funded.
////     */
////    private suspend fun notifyAgentsFunded(
////        sessionIdLong: Long,
////        agents: List<AgentConfigRequest>
////    ) = coroutineScope {
////        logger.info { "Notifying ${agents.size} agents that session $sessionIdLong is funded" }
////
////        agents.forEach { agent ->
////            if (agent.endpoint != null) {
////                launch {
////                    try {
////                        agentClient.notifySessionFunded(
////                            endpoint = agent.endpoint,
////                            notification = SessionFundedNotification(
////                                sessionId = sessionIdLong
////                            )
////                        )
////                        logger.info { "Notified agent ${agent.id} at ${agent.endpoint}" }
////                    } catch (e: Exception) {
////                        logger.error { "Failed to notify agent ${agent.id}: ${e.message}" }
////                        // Continue with other agents even if one fails
////                    }
////                }
////            }
////        }
////    }
//
//    /**
//     * Pre-check wallet balance before creating session.
//     * TODO: Implement when blockchain service supports balance queries.
//     */
//    private suspend fun preCheckBalance(requiredAmount: Long): Result<Boolean> {
//        // TODO: Implement balance checking when blockchain service supports it
//        // For now, assume sufficient balance and let the transaction fail if insufficient
//        logger.debug { "Balance pre-check skipped - not yet implemented" }
//        return Result.success(true)
//    }
//}
//
//// Data classes for internal use
//private data class AgentAvailabilityResult(
//    val agentId: String,
//    val available: Boolean,
//    val reason: String? = null,
//    val error: String? = null
//)
//
//// Exceptions
//class InsufficientAgentsException(message: String) : Exception(message)
//class SessionCreationException(message: String) : Exception(message)
//class SessionFundingException(message: String, cause: Throwable?) : Exception(message, cause)
//class InsufficientFundsException(message: String) : Exception(message)
