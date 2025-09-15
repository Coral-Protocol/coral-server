//package org.coralprotocol.coralserver.payment.orchestration
//
//import org.coralprotocol.coralserver.payment.models.*
//import io.ktor.client.*
//import io.ktor.client.call.*
//import io.ktor.client.engine.cio.*
//import io.ktor.client.plugins.*
//import io.ktor.client.plugins.contentnegotiation.*
//import io.ktor.client.plugins.logging.*
//import io.ktor.client.request.*
//import io.ktor.client.statement.*
//import io.ktor.http.*
//import io.ktor.serialization.kotlinx.json.*
//import kotlinx.serialization.json.Json
//import io.github.oshai.kotlinlogging.KotlinLogging
//
//private val logger = KotlinLogging.logger {}
//
///**
// * HTTP client for communicating with agent servers.
// * Handles availability checks and session notifications.
// */
//class AgentNotificationClient(
//    private val defaultTimeoutMs: Long = 10000
//) {
//    private val httpClient = HttpClient(CIO) {
//        install(ContentNegotiation) {
//            json(Json {
//                prettyPrint = true
//                ignoreUnknownKeys = true
//            })
//        }
//
//        install(Logging) {
//            level = LogLevel.INFO
//        }
//
//        install(HttpTimeout) {
//            requestTimeoutMillis = defaultTimeoutMs
//            connectTimeoutMillis = 5000
//        }
//
//        defaultRequest {
//            contentType(ContentType.Application.Json)
//            accept(ContentType.Application.Json)
//        }
//    }
//
//    /**
//     * Check if an agent is available to work on a session.
//     */
//    suspend fun checkAvailability(
//        endpoint: String,
//        request: AvailabilityCheckRequest
//    ): AvailabilityResponse {
//        logger.info {
//            "Checking availability at $endpoint for agent=${request.agentConfig.id}, " +
//            "session=${request.sessionId}"
//        }
//
//        return try {
//            val response = httpClient.post("$endpoint/api/v1/agents/availability") {
//                setBody(request)
//            }
//
//            if (response.status.isSuccess()) {
//                response.body<AvailabilityResponse>()
//            } else {
//                val errorBody = response.bodyAsText()
//                logger.error {
//                    "Availability check failed: ${response.status}, " +
//                    "body=$errorBody"
//                }
//                AvailabilityResponse(
//                    available = false,
//                    reason = "HTTP ${response.status}: $errorBody"
//                )
//            }
//        } catch (e: Exception) {
//            logger.error(e) { "Failed to check availability at $endpoint" }
//            AvailabilityResponse(
//                available = false,
//                reason = "Connection failed: ${e.message}"
//            )
//        }
//    }
//
//    /**
//     * Notify an agent that a session has been funded.
//     */
//    suspend fun notifySessionFunded(
//        endpoint: String,
//        notification: SessionFundedNotification
//    ): SessionFundedAck {
//        logger.info {
//            "Notifying $endpoint that session ${notification.sessionId} is funded"
//        }
//
//        return try {
//            val response = httpClient.post("$endpoint/api/v1/notifications/session-funded") {
//                setBody(notification)
//            }
//
//            if (response.status.isSuccess()) {
//                response.body<SessionFundedAck>()
//            } else {
//                val errorBody = response.bodyAsText()
//                logger.error {
//                    "Session funded notification failed: ${response.status}, " +
//                    "body=$errorBody"
//                }
//                SessionFundedAck(
//                    acknowledged = false,
//                    sessionId = notification.sessionId,
//                    workStarted = false,
//                    error = "HTTP ${response.status}: $errorBody"
//                )
//            }
//        } catch (e: Exception) {
//            logger.error(e) { "Failed to notify session funded at $endpoint" }
//            SessionFundedAck(
//                acknowledged = false,
//                sessionId = notification.sessionId,
//                workStarted = false,
//                error = "Connection failed: ${e.message}"
//            )
//        }
//    }
//
//    // TODO: Future implementation - add proper resource cleanup
//    // fun close() {
//    //     httpClient.close()
//    // }
//}