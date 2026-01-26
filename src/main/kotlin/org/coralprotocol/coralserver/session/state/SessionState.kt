@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.session.state

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.session.SessionId
import org.coralprotocol.coralserver.session.SessionResource
import org.coralprotocol.coralserver.session.SessionStatus
import org.coralprotocol.coralserver.session.SessionThread
import org.coralprotocol.coralserver.util.InstantSerializer
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
@Description("The state of a running session")
class SessionState(
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

    @Description("The status of the session")
    val status: SessionStatus,

    override val annotations: Map<String, String>,
) : SessionResource
