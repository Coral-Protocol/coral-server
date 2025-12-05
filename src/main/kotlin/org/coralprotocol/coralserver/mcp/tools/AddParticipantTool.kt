package org.coralprotocol.coralserver.mcp.tools

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.mcp.GenericSuccessOutput
import org.coralprotocol.coralserver.mcp.toMcpToolException
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.coralserver.session.ThreadId

@Serializable
data class AddParticipantInput(
    @Description("The unique identifier for the thread to add participants to")
    val threadId: ThreadId,

    @Description("The name of the agent to add as a participant to the thread")
    val participantId: UniqueAgentName
)

fun addParticipantExecutor(agent: SessionAgent, arguments: AddParticipantInput): GenericSuccessOutput {
    try {
        val thread = agent.session.getThreadById(arguments.threadId)
        thread.addParticipant(agent, agent.session.getAgent(arguments.participantId))

        return GenericSuccessOutput("Successfully added participant ${arguments.participantId} to thread ${arguments.threadId}")
    }
    catch (e: SessionException) {
        throw e.toMcpToolException()
    }
}