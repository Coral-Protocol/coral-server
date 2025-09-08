package org.coralprotocol.coralserver.payment.api

import com.coral.escrow.blockchain.BlockchainService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import mu.KotlinLogging
import org.coralprotocol.coralserver.payment.utils.ErrorHandling.parseSessionId
import org.coralprotocol.coralserver.payment.utils.ErrorHandling.respondError

private val logger = KotlinLogging.logger {}

/**
 * Common session routes available in both APP and AGENT modes.
 * These are read-only endpoints for querying session information.
 */
fun Route.commonSessionRoutes(
    blockchainService: BlockchainService
) {
    route("/sessions") {
        // Get session information (read-only, available in both modes)
        get("/{id}") {
            val sessionId = try {
                parseSessionId(call.parameters["id"])
            } catch (e: IllegalArgumentException) {
                return@get call.respondError(
                    HttpStatusCode.BadRequest,
                    e.message ?: "Invalid session ID"
                )
            }
            
            logger.info { "Fetching session info for ID: $sessionId" }
            
            val result = blockchainService.getSession(sessionId)
            
            result.fold(
                onSuccess = { session ->
                    if (session != null) {
                        logger.info { "Session $sessionId found with ${session.agents.size} agents" }
                        call.respond(session)
                    } else {
                        call.respondError(HttpStatusCode.NotFound, "Session not found")
                    }
                },
                onFailure = { error ->
                    logger.error { "Failed to get session $sessionId: ${error.message}" }
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        "Failed to retrieve session"
                    )
                }
            )
        }
    }
}