//package org.coralprotocol.coralserver.payment.api
//
////import org.coralprotocol.payment.blockchain.BlockchainService
////import org.coralprotocol.payment.blockchain.BlockchainService
//import io.github.oshai.kotlinlogging.KotlinLogging
//import io.github.smiley4.ktoropenapi.resources.post
//import io.ktor.http.*
//import io.ktor.resources.*
//import io.ktor.server.request.*
//import io.ktor.server.response.*
//import io.ktor.server.routing.*
//import org.coralprotocol.coralserver.payment.config.PaymentServerConfig
//import org.coralprotocol.coralserver.payment.models.*
//import org.coralprotocol.coralserver.payment.orchestration.InsufficientAgentsException
//import org.coralprotocol.coralserver.payment.orchestration.InsufficientFundsException
//import org.coralprotocol.coralserver.payment.orchestration.SessionCreationException
//import org.coralprotocol.coralserver.payment.orchestration.SessionFundingException
//import org.coralprotocol.coralserver.payment.orchestration.PaymentSessionManager
//import org.coralprotocol.coralserver.payment.utils.ErrorHandling.parseSessionId
//import org.coralprotocol.coralserver.payment.utils.ErrorHandling.respondError
//import org.coralprotocol.payment.blockchain.BlockchainService
//
//private val logger = KotlinLogging.logger {}
//
//@Resource("/api/v1/sessions/create")
//class CreateSession
//
//@Resource("/api/v1/sessions/{id}/fund")
//class FundSession(val id: String)
//
///**
// * Session management routes for APP mode only.
// * These endpoints are used by application developers to create and fund sessions.
// */
//fun Route.appSessionRoutes(
//    blockchainService: BlockchainService,
//    config: PaymentServerConfig,
//    paymentSessionManager: PaymentSessionManager
//) {
//    // Create session with agent availability check
//    post<CreateSession> {
//        val request = call.receive<CreateSessionRequest>()
//
//        logger.info { "Creating session with ${request.agents.size} agents" }
//
//        try {
//            val response = paymentSessionManager.createAndFundSession(
//                agents = request.agents,
//                mintPubkey = request.mintPubkey,
//                sessionId = request.sessionId,
//                fundAmount = request.fundAmount
//            )
//            call.respond(HttpStatusCode.Created, response)
//        } catch (e: InsufficientAgentsException) {
//            logger.error { "Insufficient agents: ${e.message}" }
//            call.respondError(
//                HttpStatusCode.BadRequest,
//                e.message ?: "Insufficient agents available"
//            )
//        } catch (e: SessionCreationException) {
//            logger.error { "Session creation failed: ${e.message}" }
//            call.respondError(
//                HttpStatusCode.BadRequest,
//                e.message ?: "Failed to create session"
//            )
//        } catch (e: InsufficientFundsException) {
//            logger.error { "Insufficient funds: ${e.message}" }
//            call.respondError(
//                HttpStatusCode.PaymentRequired,
//                e.message ?: "Insufficient funds in wallet"
//            )
//        } catch (e: SessionFundingException) {
//            logger.error { "Session funding failed: ${e.message}" }
//            call.respondError(
//                HttpStatusCode.InternalServerError,
//                e.message ?: "Failed to fund session"
//            )
//        } catch (e: Exception) {
//            logger.error(e) { "Unexpected error creating session" }
//            call.respondError(
//                HttpStatusCode.InternalServerError,
//                "Failed to create session"
//            )
//        }
//    }
//
//    // Fund an existing session
//    post<FundSession> { params ->
//        val sessionId = try {
//            parseSessionId(params.id)
//        } catch (e: IllegalArgumentException) {
//            return@post call.respondError(
//                HttpStatusCode.BadRequest,
//                e.message ?: "Invalid session ID"
//            )
//        }
//
//        val request = call.receive<FundSessionRequest>()
//
//        logger.info { "Funding session $sessionId with ${request.amount} tokens" }
//
//        // Check auto-fund limits if configured
//        if (config.app?.autoFund?.enabled == true) {
//            val maxAmount = config.app.autoFund.maxPerSession
//            if (request.amount > maxAmount) {
//                return@post call.respondError(
//                    HttpStatusCode.BadRequest,
//                    "Amount exceeds auto-fund limit: $maxAmount"
//                )
//            }
//        }
//
//        val result = blockchainService.fundEscrowSession(sessionId, request.amount)
//
//        result.fold(
//            onSuccess = { tx ->
//                logger.info { "Session $sessionId funded successfully: ${tx.signature}" }
//
//                // TODO: Notify agents that session is funded
//                // This could be done through SessionManager or a separate notification service
//
//                call.respond(
//                    FundSessionResponse(
//                        sessionId = sessionId,
//                        transactionSignature = tx.signature,
//                        amountDeposited = request.amount
//                    )
//                )
//            },
//            onFailure = { error ->
//                logger.error { "Failed to fund session $sessionId: ${error.message}" }
//                call.respondError(
//                    HttpStatusCode.BadRequest,
//                    error.message ?: "Failed to fund session"
//                )
//            }
//        )
//    }
//}