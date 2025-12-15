package org.coralprotocol.coralserver.session.state

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.session.SessionId
import org.coralprotocol.coralserver.session.SessionThread

@Serializable
@Description("The state of a running session")
data class SessionState(
    @Description("The unique identifier for this session")
    val id: SessionId,

    @Description("The timestamp of when this state was generated")
    val timestamp: Long,

    @Description("The namespace that this session resides in")
    val namespace: String,

    @Description("A list of the states of all agents in this session")
    val agents: List<SessionAgentState>,

    @Description("A list of the states of all threads in this session")
    val threads: List<SessionThread>
)
