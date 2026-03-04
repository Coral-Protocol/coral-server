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
data class RemoveParticipantInput(
    @Description("The unique identifier for the thread to remove participants from")
    val threadId: ThreadId,

    @Description("The name of the agent to remove as a participant of the thread")
    val participantName: UniqueAgentName
)

suspend fun removeParticipantExecutor(agent: SessionAgent, arguments: RemoveParticipantInput): GenericSuccessOutput {
    try {
        val thread = agent.session.getThreadById(arguments.threadId)
        thread.removeParticipant(agent, agent.session.getAgent(arguments.participantName))

        return GenericSuccessOutput("Successfully removed participant ${arguments.participantName} from thread ${arguments.threadId}")
    }
    catch (e: SessionException) {
        throw e.toMcpToolException()
    }
}