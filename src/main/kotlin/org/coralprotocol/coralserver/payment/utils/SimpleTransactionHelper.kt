package org.coralprotocol.coralserver.payment.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay

private val logger = KotlinLogging.logger {}

/**
 * Simple transaction helper with basic retry logic.
 * This is a temporary solution until full TransactionManager is implemented.
 */
class SimpleTransactionHelper {
    
    companion object {
        /**
         * Submit a blockchain operation with retry logic.
         */
        suspend fun <T> submitWithRetry(
            operation: suspend () -> Result<T>,
            maxAttempts: Int = 3,
            operationName: String = "operation"
        ): Result<T> {
            var lastError: Throwable? = null
            
            repeat(maxAttempts) { attempt ->
                logger.info { "Attempting $operationName (attempt ${attempt + 1}/$maxAttempts)" }
                
                val result = operation()
                if (result.isSuccess) {
                    logger.info { "$operationName succeeded on attempt ${attempt + 1}" }
                    return result
                }
                
                lastError = result.exceptionOrNull()
                val errorMessage = lastError?.message ?: "Unknown error"
                
                logger.warn { 
                    "$operationName failed on attempt ${attempt + 1}: $errorMessage" 
                }
                
                // Classify error and determine retry strategy
                val retryDelay = when {
                    // Blockhash not found - retry quickly
                    errorMessage.contains("blockhash not found", ignoreCase = true) ||
                    errorMessage.contains("blockhashnotfound", ignoreCase = true) -> {
                        logger.info { "Blockhash not found, retrying quickly..." }
                        100L
                    }
                    
                    // Insufficient funds - don't retry
                    errorMessage.contains("insufficient funds", ignoreCase = true) ||
                    errorMessage.contains("insufficientfunds", ignoreCase = true) -> {
                        logger.error { "Insufficient funds, not retrying" }
                        return result
                    }
                    
                    // Already processed - don't retry
                    errorMessage.contains("already processed", ignoreCase = true) ||
                    errorMessage.contains("alreadyprocessed", ignoreCase = true) -> {
                        logger.warn { "Transaction already processed, not retrying" }
                        return result
                    }
                    
                    // Rate limited - wait longer
                    errorMessage.contains("429") || 
                    errorMessage.contains("rate limit", ignoreCase = true) -> {
                        logger.warn { "Rate limited, waiting longer..." }
                        5000L * (attempt + 1)  // 5s, 10s, 15s
                    }
                    
                    // Default - linear backoff
                    else -> {
                        1000L * (attempt + 1)  // 1s, 2s, 3s
                    }
                }
                
                if (attempt < maxAttempts - 1) {
                    logger.info { "Waiting ${retryDelay}ms before retry..." }
                    delay(retryDelay)
                }
            }
            
            logger.error { 
                "$operationName failed after $maxAttempts attempts. Last error: ${lastError?.message}" 
            }
            return Result.failure(
                lastError ?: Exception("$operationName failed after $maxAttempts attempts")
            )
        }
        
        /**
         * Check if an error is retryable.
         */
        fun isRetryableError(error: Throwable): Boolean {
            val message = error.message ?: return true  // Retry unknown errors
            
            return when {
                // Non-retryable errors
                message.contains("insufficient funds", ignoreCase = true) -> false
                message.contains("already processed", ignoreCase = true) -> false
                message.contains("invalid signature", ignoreCase = true) -> false
                message.contains("account does not exist", ignoreCase = true) -> false
                
                // Retryable errors
                message.contains("blockhash not found", ignoreCase = true) -> true
                message.contains("network", ignoreCase = true) -> true
                message.contains("timeout", ignoreCase = true) -> true
                message.contains("429") -> true
                
                // Default - retry
                else -> true
            }
        }
        
        /**
         * Extract transaction signature from error message if available.
         */
        fun extractTransactionSignature(error: String): String? {
            // Look for base58 signature pattern (88 chars)
            val regex = "[1-9A-HJ-NP-Za-km-z]{87,88}".toRegex()
            return regex.find(error)?.value
        }
    }
}