package org.coralprotocol.coralserver.session

import java.util.concurrent.ConcurrentHashMap

/**
 * Session manager to create and retrieve sessions.
 */
object SessionManager {
    private val sessions = ConcurrentHashMap<String, CoralAgentGraphSession>()

    /**
     * Create a new session with a random ID.
     */
    fun createSession(applicationId: String, privacyKey: String): CoralAgentGraphSession {
        val sessionId = java.util.UUID.randomUUID().toString()
        val session = CoralAgentGraphSession(sessionId, applicationId, privacyKey)
        sessions[sessionId] = session
        return session
    }

    /**
     * Create a new session with a specific ID.
     */
    fun createSessionWithId(sessionId: String, applicationId: String, privacyKey: String): CoralAgentGraphSession {
        val session = CoralAgentGraphSession(sessionId, applicationId, privacyKey)
        sessions[sessionId] = session
        return session
    }

    /**
     * Get or create a session with a specific ID.
     * If the session exists, return it. Otherwise, create a new one.
     */
    fun getOrCreateSession(sessionId: String, applicationId: String, privacyKey: String): CoralAgentGraphSession {
        return sessions[sessionId] ?: createSessionWithId(sessionId, applicationId, privacyKey)
    }

    /**
     * Get a session by ID.
     */
    fun getSession(sessionId: String): CoralAgentGraphSession? {
        return sessions[sessionId]
    }

    /**
     * Get all sessions.
     */
    fun getAllSessions(): List<CoralAgentGraphSession> {
        return sessions.values.toList()
    }
}