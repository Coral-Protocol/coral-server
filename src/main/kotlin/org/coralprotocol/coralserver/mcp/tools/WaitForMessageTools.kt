package org.coralprotocol.coralserver.mcp.tools

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionThreadMessage
import org.coralprotocol.coralserver.session.SessionThreadMessageFilter

@Serializable
object WaitForSingleMessageToolInput

@Serializable
object WaitForMentioningMessageToolInput

@Serializable
data class WaitForAgentMessageToolInput(
    val agentName: UniqueAgentName
)

@Serializable
data class WaitForMessageToolOutput(
    val message: SessionThreadMessage? = null
) {
    val status: String = message?.let { "Message received" } ?: "Timeout reached"
}

suspend fun waitForSingleMessageExecutor(
    agent: SessionAgent,

    @Suppress("UNUSED_PARAMETER")
    arguments: WaitForSingleMessageToolInput
): WaitForMessageToolOutput {
    return WaitForMessageToolOutput(agent.waitForMessage())
}

suspend fun waitForMentioningMessageExecutor(
    agent: SessionAgent,

    @Suppress("UNUSED_PARAMETER")
    arguments: WaitForMentioningMessageToolInput
): WaitForMessageToolOutput {
    return WaitForMessageToolOutput(
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
    arguments: WaitForAgentMessageToolInput
): WaitForMessageToolOutput {
    return WaitForMessageToolOutput(
        agent.waitForMessage(
            setOf(
                SessionThreadMessageFilter.From(
                    name = arguments.agentName
                )
            )
        )
    )
}
