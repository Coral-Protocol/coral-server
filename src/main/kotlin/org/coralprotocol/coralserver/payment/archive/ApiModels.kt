//package org.coralprotocol.coralserver.payment.models
//
//import kotlinx.serialization.Serializable
//import coral.escrow.v1.CoralEscrow
//import coral.escrow.v1.CoralEscrow.AgentConfig.newBuilder
//
//// Request models
//@Serializable
//data class CreateSessionRequest(
//    val agents: List<AgentConfigRequest>,
//    val mintPubkey: String,
//    val sessionId: String? = null,  // UUID string, optional
//    val fundAmount: Long? = null  // Optional auto-fund amount
//)
//
//@Serializable
//data class AgentConfigRequest(
//    val id: String,
//    val cap: Long,
//    val developer: String,  // Simplified: developer is both signer and recipient
//    val endpoint: String? = null  // For notifications
//) {
//    fun toBlockchainModel(): CoralEscrow.AgentConfig = newBuilder()
//        .setId(id)
//        .setCap(cap)
//        .setDeveloper(developer)
//        .build()
//}
//
//@Serializable
//data class FundSessionRequest(
//    val amount: Long
//)
//
//
//// Response models
//@Serializable
//data class PaymentSession(
//    val sessionId: Long,  // Blockchain session ID
//    val transactionSignature: String,
//    val status: String,  // CREATED or FUNDED
//    val fundingRequired: Boolean,
//    val availableAgents: List<String>? = null,  // Agent IDs that are available
//    val error: String? = null  // Optional error message
//)
//
//@Serializable
//data class FundSessionResponse(
//    val sessionId: Long,
//    val transactionSignature: String,
//    val amountDeposited: Long
//)
//
//@Serializable
//data class ClaimResponse(
//    val success: Boolean,
//    val transactionSignature: String,
//    val claimed: Long,
//    val remaining: Long
//)
//
//@Serializable
//data class ErrorResponse(
//    val error: String
//)
//
//
//
//// Agent Availability Models
//@Serializable
//data class AvailabilityCheckRequest(
//    val sessionId: String,  // UUID string
//    val agentConfig: AgentConfigForAvailability,
//    val estimatedDurationMs: Long? = null
//)
//
//@Serializable
//data class AgentConfigForAvailability(
//    val id: String,
//    val maxCap: Long
//)
//
//@Serializable
//data class AvailabilityResponse(
//    val available: Boolean,
//    val agentId: String? = null,
//    val sessionId: String? = null,
//    val reason: String? = null,
//    val retryAfterMs: Long? = null
//)
//
//// Work Completion Models
//@Serializable
//data class WorkCompleteRequest(
//    val sessionId: String,  // UUID string
//    val agentId: String,
//    val amountSpent: Long,
//    val workDescription: String? = null
//)
//
//@Serializable
//data class WorkCompleteResponse(
//    val acknowledged: Boolean,
//    val claimSubmitted: Boolean,
//    val transactionSignature: String? = null,
//    val error: String? = null,
//    val estimatedClaimTimeMs: Long? = null
//)
//
//// Session Funded Notification Models
//@Serializable
//data class SessionFundedNotification(
//    val sessionId: Long  // Blockchain session ID
//)
//
//@Serializable
//data class SessionFundedAck(
//    val acknowledged: Boolean,
//    val sessionId: Long,
//    val workStarted: Boolean,
//    val error: String? = null
//)
//
//// Session Availability Check (App side)
//@Serializable
//data class CheckAvailabilityRequest(
//    val sessionId: String,  // Temporary string for internal use
//    val agents: List<AgentConfigRequest>,
//    val minimumAgents: Int = 1,
//    val timeoutMs: Long = 10000
//)
//
//@Serializable
//data class CheckAvailabilityResponse(
//    val sessionId: String,
//    val availableAgents: List<String>,  // Agent IDs
//    val unavailableAgents: List<String>,
//    val canProceed: Boolean,
//    val unreachableEndpoints: List<String>? = null
//)