@file:OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.util.InstantSerializer
import org.coralprotocol.coralserver.util.utcTimeNow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@JsonClassDiscriminator("type")
sealed interface SessionStatus {
    @Serializable
    @SerialName("pending_execution")
    @Description("This session status is only achieved when creating sessions with deferred execution")
    object PendingExecution : SessionStatus

    @Serializable
    @SerialName("executed")
    @Description(
        """
        The session launched it's agents and is currently running.  The session's status will remain as running until:
        
        1. All the agents in the session exit, or
        2. The session's TTL expires, or
        3. Manual exit 
        """
    )
    data class Running(
        @Serializable(with = InstantSerializer::class)
        val executionTime: Instant = utcTimeNow()
    ) : SessionStatus

    @Serializable
    @SerialName("closing")
    @Description(
        """
        The session is closing and will soon be removed from memory.  The closing status can only be observed when 
        the session has persistence configure in SessionRuntimeSettings.  Note that there is no closed state for 
        sessions, as closed sessions are deleted from memory
        """
    )
    data class Closing(
        @Serializable(with = InstantSerializer::class)
        val executionTime: Instant,

        @Serializable(with = InstantSerializer::class)
        val closingTime: Instant
    ) : SessionStatus
}