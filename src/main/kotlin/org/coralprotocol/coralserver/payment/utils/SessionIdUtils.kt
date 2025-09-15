package org.coralprotocol.coralserver.payment.utils

import java.util.*
import kotlin.math.absoluteValue

/**
 * Utilities for converting between UUID string session IDs (API layer)
 * and Long session IDs (blockchain layer).
 * 
 * Current implementation uses UUID to Long hashing as a temporary solution.
 * See ADR-016 for details on collision analysis and future migration to native UUIDs.
 * 
 * At current scale (handful of servers), collision risk is negligible.
 */
object SessionIdUtils {
    
    /**
     * Convert a UUID string to a Long for blockchain storage.
     * 
     * Note: This is a one-way hash - original UUID cannot be recovered.
     * Collision probability at current scale: ~1 in 3.4 billion.
     */
    fun uuidToSessionId(uuid: String): Long {
        return try {
            val uuidObj = UUID.fromString(uuid)
            // Combine both parts of UUID for better distribution
            val hash = uuidObj.mostSignificantBits xor uuidObj.leastSignificantBits
            // Mix bits for better distribution
            val mixed = hash xor (hash ushr 32)
            mixed.absoluteValue
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid UUID format: $uuid", e)
        }
    }
    
    /**
     * Generate a new UUID string for session creation.
     */
    fun generateSessionUuid(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * Validate UUID string format.
     */
    fun isValidUuid(uuid: String): Boolean {
        return try {
            UUID.fromString(uuid)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}