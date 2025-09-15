//package org.coralprotocol.coralserver.payment.api
//
//import io.github.smiley4.ktoropenapi.resources.post
//import io.ktor.http.*
//import io.ktor.resources.*
//import io.ktor.server.application.*
//import io.ktor.server.request.*
//import io.ktor.server.response.*
//import io.ktor.server.routing.*
//import io.github.oshai.kotlinlogging.KotlinLogging
//import org.coralprotocol.coralserver.payment.config.PaymentServerConfig
//import org.coralprotocol.coralserver.payment.models.*
//import org.coralprotocol.coralserver.payment.orchestration.SimpleAgentHandler
//import org.coralprotocol.coralserver.payment.utils.ErrorHandling.respondError
//import org.coralprotocol.coralserver.payment.utils.ErrorHandling.validatePositiveAmount
//import org.coralprotocol.coralserver.payment.utils.SessionIdUtils
//
//private val logger = KotlinLogging.logger {}
//
//@Resource("/api/v1/work/complete")
//class WorkComplete
//
//fun Route.workRoutes(
//    agentHandler: SimpleAgentHandler,
//    config: PaymentServerConfig
//) {
//    // Work completion endpoint - triggers automatic claim
//    post<WorkComplete> {
//        try {
//            val request = call.receive<WorkCompleteRequest>()
//
//            logger.info {
//                "Received work completion: session=${request.sessionId}, " +
//                        "agent=${request.agentId}, amount=${request.amountSpent}"
//            }
//
//            // Validate agent ID matches configured agent
//            val configuredAgentId = config.agent?.agentId
//            if (configuredAgentId == null) {
//                call.respondError(
//                    HttpStatusCode.InternalServerError,
//                    "Agent not configured"
//                )
//                return@post
//            }
//
//            if (request.agentId != configuredAgentId) {
//                call.respondError(
//                    HttpStatusCode.BadRequest,
//                    "Agent ID mismatch: expected $configuredAgentId"
//                )
//                return@post
//            }
//
//            // Validate amount
//            try {
//                validatePositiveAmount(request.amountSpent, "amountSpent")
//            } catch (e: IllegalArgumentException) {
//                call.respondError(
//                    HttpStatusCode.BadRequest,
//                    e.message ?: "Invalid amount"
//                )
//                return@post
//            }
//
//            // Convert session ID and submit claim
//            val sessionIdLong = try {
//                SessionIdUtils.uuidToSessionId(request.sessionId)
//            } catch (e: Exception) {
//                call.respondError(
//                    HttpStatusCode.BadRequest,
//                    "Invalid session ID format"
//                )
//                return@post
//            }
//
//            // Process work completion and submit claim
//            val result = agentHandler.submitClaim(sessionIdLong, request.amountSpent)
//
//            val response = if (result.isSuccess) {
//                result.getOrThrow()
//            } else {
//                WorkCompleteResponse(
//                    acknowledged = true,
//                    claimSubmitted = false,
//                    error = result.exceptionOrNull()?.message
//                )
//            }
//
//            logger.info {
//                "Work completion processed: claimSubmitted=${response.claimSubmitted}, " +
//                        "tx=${response.transactionSignature}"
//            }
//
//            call.respond(HttpStatusCode.OK, response)
//
//        } catch (e: Exception) {
//            logger.error(e) { "Error processing work completion" }
//            call.respondError(
//                HttpStatusCode.InternalServerError,
//                "Failed to process work completion"
//            )
//        }
//    }
//}