package org.coralprotocol.coralserver.mcp.tools

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionThreadMessage
import org.coralprotocol.coralserver.session.SessionThreadMessageFilter
import kotlin.time.Instant

@Serializable
data class WaitForSingleMessageInput(
    val currentUnixTime: Long = System.currentTimeMillis(),
)

@Serializable
data class WaitForMentioningMessageInput(
    val currentUnixTime: Long = System.currentTimeMillis(),
)

@Serializable
data class WaitForAgentMessageInput(
    val currentUnixTime: Long = System.currentTimeMillis(),
    val agentName: UniqueAgentName
)

@Serializable
data class WaitForMessageOutput(
    val message: SessionThreadMessage? = null
) {
    val status: String = message?.let { "Message received" } ?: "Timeout reached"
}

suspend fun waitForSingleMessageExecutor(
    agent: SessionAgent,

    @Suppress("UNUSED_PARAMETER")
    arguments: WaitForSingleMessageInput
): WaitForMessageOutput {
    return WaitForMessageOutput(agent.waitForMessage(Instant.fromEpochMilliseconds(arguments.currentUnixTime)))
}

suspend fun waitForMentioningMessageExecutor(
    agent: SessionAgent,

    @Suppress("UNUSED_PARAMETER")
    arguments: WaitForMentioningMessageInput
): WaitForMessageOutput {
    return WaitForMessageOutput(
        agent.waitForMessage(
            Instant.fromEpochMilliseconds(arguments.currentUnixTime),
            setOf(
                SessionThreadMessageFilter.Mentions(
                    name = agent.name
                )
            )
        )
    )
}

suspend fun waitForAgentMessageExecutor(
    agent: SessionAgent,
    arguments: WaitForAgentMessageInput
): WaitForMessageOutput {
    return WaitForMessageOutput(
        agent.waitForMessage(
            Instant.fromEpochMilliseconds(arguments.currentUnixTime),
            setOf(
                SessionThreadMessageFilter.From(
                    name = arguments.agentName
                )
            )
        )
    )
}
