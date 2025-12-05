package org.coralprotocol.coralserver.mcp.tools

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.mcp.GenericSuccessOutput
import org.coralprotocol.coralserver.mcp.toMcpToolException
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionException

@Serializable
data class CloseThreadInput(
    @Description("The unique identifier for the thread to close")
    val threadId: String,

    @Description("A shortened summary of all important details in this thread.  This could also be considered the thread's \"conclusion\".")
    val summary: String
)

fun closeThreadExecutor(agent: SessionAgent, arguments: CloseThreadInput): GenericSuccessOutput {
    try {
        val thread = agent.session.getThreadById(arguments.threadId)
        thread.close(arguments.summary)

        return GenericSuccessOutput("Successfully closed thread ${arguments.threadId}")
    }
    catch (e: SessionException) {
        throw e.toMcpToolException()
    }
}