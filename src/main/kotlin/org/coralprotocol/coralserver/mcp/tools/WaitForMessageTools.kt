package org.coralprotocol.coralserver.mcp.tools

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionThreadMessage
import org.coralprotocol.coralserver.session.SessionThreadMessageFilter

@Serializable
object WaitForSingleMessageInput

@Serializable
object WaitForMentioningMessageInput

@Serializable
data class WaitForAgentMessageInput(
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
    return WaitForMessageOutput(agent.waitForMessage())
}

suspend fun waitForMentioningMessageExecutor(
    agent: SessionAgent,

    @Suppress("UNUSED_PARAMETER")
    arguments: WaitForMentioningMessageInput
): WaitForMessageOutput {
    return WaitForMessageOutput(
        agent.waitForMessage(
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
            setOf(
                SessionThreadMessageFilter.From(
                    name = arguments.agentName
                )
            )
        )
    )
}
