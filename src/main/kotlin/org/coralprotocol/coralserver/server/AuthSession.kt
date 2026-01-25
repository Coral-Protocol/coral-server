@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.server

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.util.InstantSerializer
import org.coralprotocol.coralserver.util.utcTimeNow
import kotlin.time.ExperimentalTime

@Serializable
abstract class AuthSession {
    @Suppress("unused")
    @Serializable(with = InstantSerializer::class)
    val timestamp = utcTimeNow()

    @Serializable
    data class Token(
        val token: String
    ) : AuthSession()
}
