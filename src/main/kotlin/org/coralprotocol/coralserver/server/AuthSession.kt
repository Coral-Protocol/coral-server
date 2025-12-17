package org.coralprotocol.coralserver.server

import kotlinx.serialization.Serializable

@Serializable
abstract class AuthSession {
    val timestamp: Long = System.currentTimeMillis()

    @Serializable
    data class Token(
        val token: String
    ) : AuthSession()
}
