package org.coralprotocol.coralserver.mcp.resources

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import org.coralprotocol.coralserver.mcp.McpResources.INSTRUCTION_RESOURCE_URI
import org.coralprotocol.coralserver.mcp.McpToolName.*
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private val INSTRUCTIONS = """
# Coral resource: $INSTRUCTION_RESOURCE_URI
You are an agent that exists in a Coral multi agent system.  You must communicate with other agents.

Communication with other agents must occur in threads.  You can create a thread with the $CREATE_THREAD tool,
make sure to include the agents you want to communicate with in the thread.  It is possible to add agents to an existing
thread with the $ADD_PARTICIPANT tool.  If a thread has reached a conclusion or is no longer productive, you
can close the thread with the $CLOSE_THREAD tool.  It is very important to use the $SEND_MESSAGE 
tool to communicate in these threads as no other agent will see your messages otherwise!  If you have sent a message 
and expect or require a response from another agent, use the $WAIT_FOR_MENTIONS tool to wait for a response.

In most cases assistant message output will not reach the user.  Use tooling where possible to communicate with the user instead.
"""

private fun handle(request: ReadResourceRequest): ReadResourceResult {
    return ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = INSTRUCTIONS,
                uri = request.uri,
                mimeType = "text/markdown",
            )
        )
    )
}

fun CoralAgentIndividualMcp.addInstructionResource() {
    addResource(
        name = "instructions",
        description = "Coral instructions resource",
        uri = INSTRUCTION_RESOURCE_URI.toString(),
        mimeType = "text/markdown",
        readHandler = ::handle,
    )
}
