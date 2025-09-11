//package org.coralprotocol.coralserver.payment.api
//
//import org.coralprotocol.payment.blockchain.BlockchainService
//import org.coralprotocol.coralserver.payment.config.PaymentServerConfig
//import org.coralprotocol.coralserver.payment.orchestration.SimpleAgentHandler
//import org.coralprotocol.coralserver.payment.orchestration.PaymentSessionManager
//import org.coralprotocol.coralserver.payment.orchestration.AgentNotificationClient
//import io.ktor.server.application.*
//import io.ktor.server.routing.*
//
///**
// * Configure API routing based on configuration.
// * Routes are included based on which configuration sections are present.
// *
// * Route structure:
// * - CommonSessionRoutes: GET /sessions/{id} - Always included
// * - AppSessionRoutes: POST /sessions/create, POST /sessions/{id}/fund - When app config exists
// * - AgentRoutes: POST /agents/availability - When agent config exists
// * - WorkRoutes: POST /work/complete - When agent config exists
// * - NotificationRoutes: POST /notifications/session-funded - When agent config exists
// * - ClaimRoutes: POST /claims/submit - When agent config exists
// */
//fun Application.configureRouting(
//    blockchainService: BlockchainService,
//    config: PaymentServerConfig
//) {
//    routing {
//        // Health check (always included)
//        healthRoutes()
//
//        // API v1
//        route("/api/v1") {
//            // Common routes always included
//            commonSessionRoutes(blockchainService)
//
//            // Include app routes if app config exists
//            config.app?.let { appConfig ->
//                // TODO: Future - implement proper resource lifecycle management for HTTP client
//                val agentClient = AgentNotificationClient(
//                    defaultTimeoutMs = config.notifications?.timeout?.let { parseDuration(it) } ?: 5000
//                )
//                val paymentSessionManager = PaymentSessionManager(blockchainService, agentClient)
//                appSessionRoutes(blockchainService, config, paymentSessionManager)
//            }
//
//            // Include agent routes if agent config exists
//            config.agent?.let { agentConfig ->
//                val agentHandler = SimpleAgentHandler(blockchainService, agentConfig)
//                agentRoutes(agentHandler, config)
//                workRoutes(agentHandler, config)
////                notificationRoutes(blockchainService, config, agentHandler)
//                claimRoutes(blockchainService, config)
//            }
//        }
//    }
//}
//
///**
// * Parse duration string like "5s", "30s", "1m" to milliseconds.
// */
//private fun parseDuration(duration: String): Long {
//    return when {
//        duration.endsWith("ms") -> duration.removeSuffix("ms").toLong()
//        duration.endsWith("s") -> duration.removeSuffix("s").toLong() * 1000
//        duration.endsWith("m") -> duration.removeSuffix("m").toLong() * 60 * 1000
//        else -> duration.toLong()  // Assume milliseconds
//    }
//}