//package org.coralprotocol.coralserver.payment.api
//
//import io.github.smiley4.ktoropenapi.resources.get
//import io.ktor.http.*
//import io.ktor.resources.*
//import io.ktor.server.response.*
//import io.ktor.server.routing.*
//import io.github.oshai.kotlinlogging.KotlinLogging
//import org.coralprotocol.coralserver.payment.utils.ErrorHandling.parseSessionId
//import org.coralprotocol.coralserver.payment.utils.ErrorHandling.respondError
//import org.coralprotocol.payment.blockchain.BlockchainService
//
//private val logger = KotlinLogging.logger {}
//
//@Resource("/api/v1/sessions/{id}")
//class SessionInfo(val id: String)
//
///**
// * Common session routes available in both APP and AGENT modes.
// * These are read-only endpoints for querying session information.
// */
//fun Route.commonSessionRoutes(
//    blockchainService: BlockchainService
//) {
//    // Get session information (read-only, available in both modes)
//    get<SessionInfo> { params ->
//        val sessionId = try {
//            parseSessionId(params.id)
//        } catch (e: IllegalArgumentException) {
//            return@get call.respondError(
//                HttpStatusCode.BadRequest,
//                e.message ?: "Invalid session ID"
//            )
//        }
//
//        logger.info { "Fetching session info for ID: $sessionId" }
//
//        val result = blockchainService.getEscrowSession(sessionId)
//
//        result.fold(
//            onSuccess = { session ->
//                if (session != null) {
//                    logger.info { "Session $sessionId found with ${session.agents.size} agents" }
//                    call.respond(session)
//                } else {
//                    call.respondError(HttpStatusCode.NotFound, "Session not found")
//                }
//            },
//            onFailure = { error ->
//                logger.error { "Failed to get session $sessionId: ${error.message}" }
//                call.respondError(
//                    HttpStatusCode.InternalServerError,
//                    "Failed to retrieve session"
//                )
//            }
//        )
//    }
//}