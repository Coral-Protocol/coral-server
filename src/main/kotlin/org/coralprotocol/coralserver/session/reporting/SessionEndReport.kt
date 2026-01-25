@file:OptIn(ExperimentalTime::class)

package org.coralprotocol.coralserver.session.reporting

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.session.SessionId
import org.coralprotocol.coralserver.util.InstantSerializer
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class SessionEndReport(
    @Description("The time that the session started, ISO 8601")
    @Serializable(with = InstantSerializer::class)
    val startTime: Instant,

    @Description("The time that the session ended, ISO 8601")
    @Serializable(with = InstantSerializer::class)
    val endTime: Instant,

    @Description("The namespace that the session belonged in")
    val namespace: String,

    @Description("The unique identifier for the session")
    val sessionId: SessionId,

    @Description("The statistics for each agent in the session, note that an individual agent may appear more than once if they restarted during a session")
    val agentStats: List<SessionAgentUsageReport>
)