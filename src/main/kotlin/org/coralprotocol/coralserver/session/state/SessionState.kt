@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.session.state

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.session.SessionId
import org.coralprotocol.coralserver.session.SessionThread
import org.coralprotocol.coralserver.util.InstantSerializer
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@Description("The state of a running session")
data class SessionState(
    @Description("The unique identifier for this session")
    val id: SessionId,

    @Description("The timestamp of when this state was generated")
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,

    @Description("The namespace that this session resides in")
    val namespace: String,

    @Description("A list of the states of all agents in this session")
    val agents: List<SessionAgentState>,

    @Description("A list of the states of all threads in this session")
    val threads: List<SessionThread>,

    @Description("True if the session is closing, closing sessions have no running agents and will only be kept in memory for as long as the persistence settings specify")
    val closing: Boolean
)
