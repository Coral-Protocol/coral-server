package org.coralprotocol.coralserver.session.reporting

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.session.SessionId

@Serializable
data class SessionEndReport(
    @Description("UNIX time for when the session started")
    val startTime: Long,

    @Description("UNIX time for when the session ended")
    val endTime: Long,

    @Description("The namespace that the session belonged in")
    val namespace: String,

    @Description("The unique identifier for the session")
    val sessionId: SessionId,

    @Description("The statistics for each agent in the session, note that an individual agent may appear more than once if they restarted during a session")
    val agentStats: List<SessionAgentUsageReport>
)