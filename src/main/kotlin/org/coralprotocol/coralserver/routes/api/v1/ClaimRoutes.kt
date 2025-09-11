package org.coralprotocol.coralserver.routes.api.v1

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktoropenapi.resources.get
import io.github.smiley4.ktoropenapi.resources.post
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.coralprotocol.coralserver.config.PaymentConfig
import org.coralprotocol.coralserver.payment.api.PaymentClaimStatus
import org.coralprotocol.coralserver.payment.models.ClaimResponse
import org.coralprotocol.coralserver.payment.models.PaymentClaimRequest
import org.coralprotocol.coralserver.payment.utils.ErrorHandling.parseSessionId
import org.coralprotocol.coralserver.payment.utils.ErrorHandling.respondError
import org.coralprotocol.coralserver.payment.utils.ErrorHandling.validateParameter
import org.coralprotocol.payment.blockchain.BlockchainService

private val logger = KotlinLogging.logger {}

@Resource("/api/v1/internal/claim")
class SubmitPaymentClaim

@Resource("/api/v1/internal/claim/{sessionId}/{agentId}")
class PaymentClaimStatus(val sessionId: String, val agentId: String)

fun Route.claimRoutes(
    blockchainService: BlockchainService,
    config: PaymentConfig
) {
    // Submit claim (AGENT mode only)

    /**
     * agent submits multiple claims to server, server aggregates claims and submits to blockchain once no more claims will be made.
     */
    post<SubmitPaymentClaim> {
        val request = call.receive<PaymentClaimRequest>()

        logger.info {
            "Processing claim for session ${request.sessionId}, agent ${request.agentId}, amount: ${request.amount}"
        }

        // Check auto-claim configuration
//        if (config.agent?.autoClaim?.enabled == true) {
//            val minAmount = config.agent.autoClaim.minAmount
//            if (request.amount < minAmount) {
//                return@post call.respondError(
//                    HttpStatusCode.BadRequest,
//                    "Amount below minimum claim threshold: $minAmount"
//                )
//            }
//        }

        // First, check if already claimed
        val claimedResult = blockchainService.checkEscrowClaimed(request.sessionId, request.agentId)
        if (claimedResult.isSuccess && claimedResult.getOrNull() == true) {
            return@post call.respondError(
                HttpStatusCode.Conflict,
                "Agent ${request.agentId} has already claimed from session ${request.sessionId}"
            )
        }

        // Submit the claim
        val result = blockchainService.submitEscrowClaim(
            sessionId = request.sessionId,
            agentId = request.agentId,
            amount = request.amount
        )

        result.fold(
            onSuccess = { tx ->
                logger.info {
                    "Claim successful for agent ${request.agentId} in session ${request.sessionId}: ${tx.signature}"
                }

                // TODO: Calculate remaining amount from session query
                call.respond(
                    ClaimResponse(
                        success = true,
                        transactionSignature = tx.signature,
                        claimed = request.amount,
                        remaining = 0 // Would need to query session for actual remaining
                    )
                )
            },
            onFailure = { error ->
                logger.error {
                    "Failed to process claim for agent ${request.agentId}: ${error.message}"
                }
                call.respondError(
                    HttpStatusCode.BadRequest,
                    error.message ?: "Failed to process claim"
                )
            }
        )
    }

    // Check claim status
    get<PaymentClaimStatus> { params ->
        val sessionId = try {
            parseSessionId(params.sessionId)
        } catch (e: IllegalArgumentException) {
            return@get call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid session ID")
        }

        val agentId = try {
            validateParameter(params.agentId, "agentId")
        } catch (e: IllegalArgumentException) {
            return@get call.respondError(HttpStatusCode.BadRequest, e.message ?: "Missing agent ID")
        }

        val result = blockchainService.checkEscrowClaimed(sessionId, agentId)

        result.fold(
            onSuccess = { claimed ->
                call.respond(
                    mapOf(
                        "sessionId" to sessionId,
                        "agentId" to agentId,
                        "claimed" to claimed
                    )
                )
            },
            onFailure = { error ->
                logger.error { "Failed to check claim status: ${error.message}" }
                call.respondError(
                    HttpStatusCode.InternalServerError,
                    "Failed to check claim status"
                )
            }
        )
    }
}