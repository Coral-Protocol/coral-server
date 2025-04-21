package org.coralprotocol.coralserver.session

import java.util.concurrent.ConcurrentHashMap

/**
 * Session manager to create and retrieve sessions.
 */
object SessionManager {
    private val sessions = ConcurrentHashMap<String, Session>()

    /**
     * Create a new session with a random ID.
     */
    fun createSession(applicationId: String, privacyKey: String): Session {
        val sessionId = java.util.UUID.randomUUID().toString()
        val session = Session(sessionId, applicationId, privacyKey)
        sessions[sessionId] = session
        return session
    }

    /**
     * Create a new session with a specific ID.
     */
    fun createSessionWithId(sessionId: String, applicationId: String, privacyKey: String): Session {
        val session = Session(sessionId, applicationId, privacyKey)
        sessions[sessionId] = session
        return session
    }

    /**
     * Get or create a session with a specific ID.
     * If the session exists, return it. Otherwise, create a new one.
     */
    fun getOrCreateSession(sessionId: String, applicationId: String, privacyKey: String): Session {
        return sessions[sessionId] ?: createSessionWithId(sessionId, applicationId, privacyKey)
    }

    /**
     * Get a session by ID.
     */
    fun getSession(sessionId: String): Session? {
        return sessions[sessionId]
    }

    /**
     * Get all sessions.
     */
    fun getAllSessions(): List<Session> {
        return sessions.values.toList()
    }
}