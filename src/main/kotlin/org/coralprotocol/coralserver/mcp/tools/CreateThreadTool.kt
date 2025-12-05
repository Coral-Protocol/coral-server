package org.coralprotocol.coralserver.mcp.tools

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.mcp.toMcpToolException
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionException
import org.coralprotocol.coralserver.session.SessionThread

@Serializable
data class CreateThreadInput(
    @Description("The name of the thread to create, this should be a short summary of the intended topic")
    val threadName: String,

    @Description("The list of participants to include in the thread, this should include any agent that is expected to be involved in the thread's topic.  You do not need to include yourself in this list.")
    val participantIds: Set<UniqueAgentName>
)

@Serializable
data class CreateThreadOutput(
    val thread: SessionThread
)

fun createThreadExecutor(agent: SessionAgent, arguments: CreateThreadInput): CreateThreadOutput {
    try {
        return CreateThreadOutput(
            agent.session.createThread(arguments.threadName, agent.name, arguments.participantIds)
        )
    }
    catch (e: SessionException) {
        throw e.toMcpToolException()
    }
}