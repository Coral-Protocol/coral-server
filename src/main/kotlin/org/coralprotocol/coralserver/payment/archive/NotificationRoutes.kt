//package org.coralprotocol.coralserver.payment.api
//
//import org.coralprotocol.coralserver.escrow.blockchain.BlockchainService
//import org.coralprotocol.coralserver.payment.config.PaymentServerConfig
//import org.coralprotocol.coralserver.payment.models.*
//import org.coralprotocol.coralserver.payment.orchestration.SimpleAgentHandler
//import org.coralprotocol.coralserver.payment.utils.ErrorHandling.respondError
//import io.ktor.http.*
//import io.ktor.server.application.*
//import io.ktor.server.request.*
//import io.ktor.server.response.*
//import io.ktor.server.routing.*
//import io.github.oshai.kotlinlogging.KotlinLogging
//
//private val logger = KotlinLogging.logger {}
//
//// maybe not needed
//// TODO: Move over some validation logic to Claim
//fun Route.notificationRoutes(
//    blockchainService: BlockchainService,
//    config: PaymentServerConfig,
//    agentHandler: SimpleAgentHandler? = null
//) {
//    route("/notifications") {
//        // Session funded notification (AGENT mode only)
//        post("/session-funded") {
//            try {
//                val notification = call.receive<SessionFundedNotification>()
//
//                logger.info {
//                    "Received session-funded notification: sessionId=${notification.sessionId}"
//                }
//
//                // Validate agent configuration
//                val myAgentId = config.agent?.agentId
//                if (myAgentId == null) {
//                    call.respondError(
//                        HttpStatusCode.InternalServerError,
//                        "Agent ID not configured"
//                    )
//                    return@post
//                }
//
//                if (agentHandler == null) {
//                    call.respondError(
//                        HttpStatusCode.InternalServerError,
//                        "Agent handler not configured"
//                    )
//                    return@post
//                }
//
//                // Verify session exists on blockchain and we're part of it
//                val sessionResult = blockchainService.getSession(notification.sessionId)
//                if (sessionResult.isFailure) {
//                    logger.error { "Failed to query session: ${sessionResult.exceptionOrNull()?.message}" }
//                    call.respondError(
//                        HttpStatusCode.InternalServerError,
//                        "Failed to verify session"
//                    )
//                    return@post
//                }
//
//                val session = sessionResult.getOrNull()
//                if (session == null) {
//                    call.respondError(
//                        HttpStatusCode.NotFound,
//                        "Session not found on blockchain"
//                    )
//                    return@post
//                }
//
//                // Check if we're actually in the agent list
//                val ourAgent = session.agents.find { it.id == myAgentId }
//                if (ourAgent == null) {
//                    call.respondError(
//                        HttpStatusCode.BadRequest,
//                        "Agent not part of this session"
//                    )
//                    return@post
//                }
//
//                // Verify session is funded
//                if (session.balance <= 0) {
//                    logger.warn { "Session ${notification.sessionId} has zero balance" }
//                    call.respondError(
//                        HttpStatusCode.BadRequest,
//                        "Session not funded"
//                    )
//                    return@post
//                }
//
//                // Handle the funded notification
//                val result = agentHandler.handleSessionFunded(notification.sessionId)
//
//                logger.info {
//                    "Session funded notification processed: sessionId=${notification.sessionId}, " +
//                    "workStarted=${result.workStarted}"
//                }
//
//                call.respond(HttpStatusCode.OK, result)
//
//            } catch (e: Exception) {
//                logger.error(e) { "Error processing session-funded notification" }
//                call.respondError(
//                    HttpStatusCode.InternalServerError,
//                    "Failed to process session-funded notification"
//                )
//            }
//        }
//    }
//}