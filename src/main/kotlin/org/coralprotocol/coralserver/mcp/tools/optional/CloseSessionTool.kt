package org.coralprotocol.coralserver.mcp.tools.optional

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.mcp.GenericSuccessOutput
import org.coralprotocol.coralserver.mcp.toMcpToolException
import org.coralprotocol.coralserver.session.SessionAgent
import org.coralprotocol.coralserver.session.SessionException

@Serializable
data class CloseSessionInput(
    @Description("The reason that the agent closed the session, for logging purposes")
    val reason: String,
)

fun closeSessionExecutor(agent: SessionAgent, arguments: CloseSessionInput): GenericSuccessOutput {
    try {
        agent.logger.info { "Closing session with reason: ${arguments.reason}" }
        agent.session.cancelAgents()

        return GenericSuccessOutput("Successfully closed session")
    } catch (e: SessionException) {
        throw e.toMcpToolException()
    }
}