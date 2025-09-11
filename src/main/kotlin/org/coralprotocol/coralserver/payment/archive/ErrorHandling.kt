//package org.coralprotocol.coralserver.payment.utils
//
//import org.coralprotocol.coralserver.payment.models.ErrorResponse
//import io.ktor.http.*
//import io.ktor.server.application.*
//import io.ktor.server.response.*
//import io.github.oshai.kotlinlogging.KotlinLogging
//
//private val logger = KotlinLogging.logger {}
//
///**
// * Standard error response handling utilities.
// * Ensures consistent error responses across all API endpoints.
// */
//object ErrorHandling {
//
//    /**
//     * Respond with a standard error response.
//     */
//    suspend fun ApplicationCall.respondError(
//        status: HttpStatusCode,
//        message: String,
//        logError: Boolean = true
//    ) {
//        if (logError) {
//            when {
//                status.value >= 500 -> logger.error { "Server error: $message" }
//                status.value >= 400 -> logger.warn { "Client error: $message" }
//                else -> logger.info { "Response: $message" }
//            }
//        }
//        respond(status, ErrorResponse(error = message))
//    }
//
//    /**
//     * Handle Result types consistently.
//     */
//    suspend inline fun <T> ApplicationCall.handleResult(
//        result: Result<T>,
//        successStatus: HttpStatusCode = HttpStatusCode.OK,
//        crossinline onSuccess: suspend (T) -> Unit
//    ) {
//        result.fold(
//            onSuccess = { value ->
//                onSuccess(value)
//            },
//            onFailure = { error ->
//                val message = error.message ?: "Operation failed"
//                when (error) {
//                    is IllegalArgumentException -> respondError(HttpStatusCode.BadRequest, message)
//                    is NoSuchElementException -> respondError(HttpStatusCode.NotFound, message)
//                    is SecurityException -> respondError(HttpStatusCode.Forbidden, message)
//                    else -> respondError(HttpStatusCode.InternalServerError, "Internal error occurred")
//                }
//            }
//        )
//    }
//
//    /**
//     * Standard parameter validation.
//     */
//    fun validateParameter(value: String?, paramName: String): String {
//        return value?.trim()?.takeIf { it.isNotEmpty() }
//            ?: throw IllegalArgumentException("Missing required parameter: $paramName")
//    }
//
//    /**
//     * Validate positive amounts.
//     */
//    fun validatePositiveAmount(amount: Long, fieldName: String = "amount"): Long {
//        if (amount <= 0) {
//            throw IllegalArgumentException("$fieldName must be positive")
//        }
//        return amount
//    }
//
//    /**
//     * Parse and validate session ID.
//     */
//    fun parseSessionId(sessionIdParam: String?): Long {
//        val param = validateParameter(sessionIdParam, "session ID")
//
//        // Try to parse as Long first
//        param.toLongOrNull()?.let { return it }
//
//        // Try to parse as UUID
//        return try {
//            SessionIdUtils.uuidToSessionId(param)
//        } catch (e: Exception) {
//            throw IllegalArgumentException("Invalid session ID format: $param")
//        }
//    }
//}