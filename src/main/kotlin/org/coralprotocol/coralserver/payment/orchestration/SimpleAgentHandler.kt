package org.coralprotocol.coralserver.payment.orchestration

import com.coral.escrow.blockchain.BlockchainService
import org.coralprotocol.coralserver.payment.config.AgentConfig
import org.coralprotocol.coralserver.payment.models.*
import org.coralprotocol.coralserver.payment.utils.SimpleTransactionHelper
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

// TODO: Maybe can remove a lot here
// Crossmint handle retries for us
/**
 * Simplified agent handler for MVP.
 * - Always returns available (will integrate with coral-server later)
 * - Handles auto-claim submission when work is complete
 * - Tracks basic metrics
 */
class SimpleAgentHandler(
    private val blockchain: BlockchainService,
    private val config: AgentConfig
) {
    // Track active sessions for metrics only
    private val activeSessions = ConcurrentHashMap<Long, Instant>()
    
    // Basic metrics
    private var completedClaims = 0
    private var failedClaims = 0
    
    /**
     * Check agent availability.
     * MVP: Always returns available. Will integrate with coral-server later.
     */
    fun checkAvailability(request: AvailabilityCheckRequest): AvailabilityResponse {
        logger.info { "Availability check for session ${request.sessionId}" }
        
        // MVP: Always available
        return AvailabilityResponse(
            available = true,
            agentId = config.agentId,
            sessionId = request.sessionId
        )
    }
    
    /**
     * Handle session funded notification.
     * MVP: Just acknowledge and track. Real work coordination happens in coral-server.
     */
    fun handleSessionFunded(sessionId: Long): SessionFundedAck {
        logger.info { "Session $sessionId funded, tracking for agent ${config.agentId}" }
        
        activeSessions[sessionId] = Instant.now()
        
        return SessionFundedAck(
            acknowledged = true,
            sessionId = sessionId,
            workStarted = true
        )
    }
    
    /**
     * Submit claim when work is complete.
     * This is the main auto-claim functionality.
     */
    suspend fun submitClaim(
        sessionId: Long,
        amount: Long
    ): Result<WorkCompleteResponse> = coroutineScope {
        logger.info { "Submitting claim: session=$sessionId, amount=$amount" }
        
        // Validate amount
        if (amount <= 0) {
            return@coroutineScope Result.failure(
                IllegalArgumentException("Claim amount must be positive")
            )
        }
        
        // Submit claim with retry
        val claimResult = SimpleTransactionHelper.submitWithRetry(
            operation = {
                blockchain.submitClaim(
                    sessionId = sessionId,
                    agentId = config.agentId,
                    amount = amount
                )
            },
            maxAttempts = 3,
            operationName = "submitClaim"
        )
        
        // Update metrics
        if (claimResult.isSuccess) {
            completedClaims++
            activeSessions.remove(sessionId)
            
            val claim = claimResult.getOrThrow()
            logger.info { 
                "Claim successful: tx=${claim.signature}, " +
                "claimed=${claim.amountClaimed}" 
            }
            
            Result.success(WorkCompleteResponse(
                acknowledged = true,
                claimSubmitted = true,
                transactionSignature = claim.signature
            ))
        } else {
            failedClaims++
            logger.error { "Claim failed: ${claimResult.exceptionOrNull()?.message}" }
            
            Result.failure(claimResult.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }
    
    /**
     * Get current handler state for monitoring.
     */
    fun getState() = mapOf(
        "agentId" to config.agentId,
        "activeSessions" to activeSessions.size,
        "completedClaims" to completedClaims,
        "failedClaims" to failedClaims
    )
}