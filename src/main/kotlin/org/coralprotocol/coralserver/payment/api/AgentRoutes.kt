package org.coralprotocol.coralserver.payment.api

import org.coralprotocol.coralserver.payment.config.PaymentServerConfig
import org.coralprotocol.coralserver.payment.models.*
import org.coralprotocol.coralserver.payment.orchestration.SimpleAgentHandler
import org.coralprotocol.coralserver.payment.utils.ErrorHandling.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun Route.agentRoutes(
    agentHandler: SimpleAgentHandler,
    config: PaymentServerConfig
) {
    route("/agents") {
        // Agent availability check endpoint
        post("/availability") {
            try {
                val request = call.receive<AvailabilityCheckRequest>()
                
                logger.info { 
                    "Received availability check for session=${request.sessionId}, " +
                    "agent=${request.agentConfig.id}, maxCap=${request.agentConfig.maxCap}" 
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
                
                if (request.agentConfig.id != configuredAgentId) {
                    logger.warn { 
                        "Availability check for wrong agent. Expected=$configuredAgentId, " +
                        "Received=${request.agentConfig.id}" 
                    }
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        "Agent ID mismatch: expected $configuredAgentId"
                    )
                    return@post
                }
                
                // Check availability through agent handler
                val response = agentHandler.checkAvailability(request)
                
                logger.info { 
                    "Availability check result: available=${response.available}, " +
                    "reason=${response.reason}" 
                }
                
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                logger.error(e) { "Error processing availability check" }
                call.respondError(
                    HttpStatusCode.InternalServerError,
                    "Failed to process availability check"
                )
            }
        }
    }
}