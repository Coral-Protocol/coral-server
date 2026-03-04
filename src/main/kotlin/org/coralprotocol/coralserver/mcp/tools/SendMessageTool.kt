package org.coralprotocol.coralserver.mcp.tools

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.mcp.toMcpToolException
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.coralserver.session.SessionThreadMessage

@Serializable
data class SendMessageInput(
    @Description("The unique identifier for the thread to send a message in")
    val threadId: String,

    @Description("The content of the message to send")
    val content: String,

    @Description("")
    // cannot be a set because generated schema will include "uniqueItems: true" that OpenAI throws errors for
    val mentions: List<UniqueAgentName>
)

@Serializable
data class SendMessageOutput(
    val status: String,
    val message: SessionThreadMessage
)

suspend fun sendMessageExecutor(agent: SessionAgent, arguments: SendMessageInput): SendMessageOutput {
    try {
        return SendMessageOutput(
            status = "Message sent successfully",
            message = agent.sendMessage(arguments.content, arguments.threadId, arguments.mentions.toSet())
        )
    }
    catch (e: SessionException) {
        throw e.toMcpToolException()
    }
}