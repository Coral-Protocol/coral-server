package com.coral.payment.api

import com.coral.payment.config.PaymentServerConfig
import com.coral.payment.models.*
import com.coral.payment.orchestration.SimpleAgentHandler
import com.coral.payment.utils.SessionIdUtils
import com.coral.payment.utils.ErrorHandling.respondError
import com.coral.payment.utils.ErrorHandling.validatePositiveAmount
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun Route.workRoutes(
    agentHandler: SimpleAgentHandler,
    config: PaymentServerConfig
) {
    route("/work") {
        // Work completion endpoint - triggers automatic claim
        post("/complete") {
            try {
                val request = call.receive<WorkCompleteRequest>()
                
                logger.info { 
                    "Received work completion: session=${request.sessionId}, " +
                    "agent=${request.agentId}, amount=${request.amountSpent}" 
                }
                
                // Validate agent ID matches configured agent
                val configuredAgentId = config.agent?.agentId
                if (configuredAgentId == null) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        "Agent not configured"
                    )
                    return@post
                }
                
                if (request.agentId != configuredAgentId) {
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        "Agent ID mismatch: expected $configuredAgentId"
                    )
                    return@post
                }
                
                // Validate amount
                try {
                    validatePositiveAmount(request.amountSpent, "amountSpent")
                } catch (e: IllegalArgumentException) {
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        e.message ?: "Invalid amount"
                    )
                    return@post
                }
                
                // Convert session ID and submit claim
                val sessionIdLong = try {
                    SessionIdUtils.uuidToSessionId(request.sessionId)
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        "Invalid session ID format"
                    )
                    return@post
                }
                
                // Process work completion and submit claim
                val result = agentHandler.submitClaim(sessionIdLong, request.amountSpent)
                
                val response = if (result.isSuccess) {
                    result.getOrThrow()
                } else {
                    WorkCompleteResponse(
                        acknowledged = true,
                        claimSubmitted = false,
                        error = result.exceptionOrNull()?.message
                    )
                }
                
                logger.info { 
                    "Work completion processed: claimSubmitted=${response.claimSubmitted}, " +
                    "tx=${response.transactionSignature}" 
                }
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                logger.error(e) { "Error processing work completion" }
                call.respondError(
                    HttpStatusCode.InternalServerError,
                    "Failed to process work completion"
                )
            }
        }
    }
}