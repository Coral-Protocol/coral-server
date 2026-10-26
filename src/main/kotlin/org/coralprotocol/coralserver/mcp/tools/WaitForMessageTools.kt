package org.coralprotocol.coralserver.mcp.tools

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionThreadMessage
import org.coralprotocol.coralserver.session.SessionThreadMessageFilter
import org.coralprotocol.coralserver.util.InstantSerializer
import org.coralprotocol.coralserver.util.utcTimeNow
import kotlin.time.Instant

const val MAX_WAIT_MILLISECONDS = 45000L

const val CURRENT_TIME_DESCRIPTION =
    "The current time in ISO-8601 (UTC), e.g. 2026-10-25T12:30:00.123Z, exactly as it is written in your state.  Messages you have not received yet that were sent at or after this time are returned immediately"

const val MAX_WAIT_DESCRIPTION = "The maximum number of milliseconds to wait for, at most $MAX_WAIT_MILLISECONDS"

@Serializable
data class WaitForSingleMessageInput(
    @Description(CURRENT_TIME_DESCRIPTION)
    @Serializable(with = InstantSerializer::class)
    val currentIso8601Time: Instant = utcTimeNow(),

    @Description(MAX_WAIT_DESCRIPTION)
    val maxWaitMs: Long = MAX_WAIT_MILLISECONDS
)

@Serializable
data class WaitForMentioningMessageInput(
    @Description(CURRENT_TIME_DESCRIPTION)
    @Serializable(with = InstantSerializer::class)
    val currentIso8601Time: Instant = utcTimeNow(),

    @Description(MAX_WAIT_DESCRIPTION)
    val maxWaitMs: Long = MAX_WAIT_MILLISECONDS
)

@Serializable
data class WaitForAgentMessageInput(
    @Description(CURRENT_TIME_DESCRIPTION)
    @Serializable(with = InstantSerializer::class)
    val currentIso8601Time: Instant = utcTimeNow(),

    @Description("The name of the agent to wait for a message from")
    val agentName: UniqueAgentName,

    @Description(MAX_WAIT_DESCRIPTION)
    val maxWaitMs: Long = MAX_WAIT_MILLISECONDS
)

@Serializable
data class WaitForMessageOutput(
    val message: SessionThreadMessage? = null
) {
    val status: String = message?.let { "Message received" } ?: "Timeout reached"
}

suspend fun waitForSingleMessageExecutor(
    agent: SessionAgent,
    arguments: WaitForSingleMessageInput
): WaitForMessageOutput {
    return WaitForMessageOutput(
        agent.waitForMessage(
            replayAfter = arguments.currentIso8601Time,
            timeoutMs = arguments.maxWaitMs.coerceAtMost(MAX_WAIT_MILLISECONDS)
        )
    )
}

suspend fun waitForMentioningMessageExecutor(
    agent: SessionAgent,
    arguments: WaitForMentioningMessageInput
): WaitForMessageOutput {
    return WaitForMessageOutput(
        agent.waitForMessage(
            replayAfter = arguments.currentIso8601Time,
            filters = setOf(
                SessionThreadMessageFilter.Mentions(
                    name = agent.name
                )
            ),
            timeoutMs = arguments.maxWaitMs.coerceAtMost(MAX_WAIT_MILLISECONDS)
        )
    )
}

suspend fun waitForAgentMessageExecutor(
    agent: SessionAgent,
    arguments: WaitForAgentMessageInput
): WaitForMessageOutput {
    return WaitForMessageOutput(
        agent.waitForMessage(
            replayAfter = arguments.currentIso8601Time,
            filters = setOf(
                SessionThreadMessageFilter.From(
                    name = arguments.agentName
                )
            ),
            timeoutMs = arguments.maxWaitMs.coerceAtMost(MAX_WAIT_MILLISECONDS)
        )
    )
}
