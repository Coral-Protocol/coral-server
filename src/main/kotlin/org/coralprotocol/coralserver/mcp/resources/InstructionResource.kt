package org.coralprotocol.coralserver.mcp.resources

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import org.coralprotocol.coralserver.mcp.tools.ADD_PARTICIPANT_TOOL_NAME
import org.coralprotocol.coralserver.mcp.tools.CLOSE_THREAD_TOOL_NAME
import org.coralprotocol.coralserver.mcp.tools.CREATE_THREAD_TOOL_NAME
import org.coralprotocol.coralserver.mcp.tools.SEND_MESSAGE_TOOL_NAME
import org.coralprotocol.coralserver.mcp.tools.WAIT_FOR_MENTIONS_TOOL_NAME
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

const val INSTRUCTION_RESOURCE_URI = "Instructions.resource"
const val INSTRUCTIONS = """
You are an agent that exists in a Coral multi agent system.  You must communicate with other agents to solve tasks.

Communication with other agents must occur in threads.  You can create a thread with the $CREATE_THREAD_TOOL_NAME tool,
make sure to include the agents you want to communicate with in the thread.  It is possible to add agents to an existing
thread with the $ADD_PARTICIPANT_TOOL_NAME tool.  If a thread has reached a conclusion or is no longer productive, you
can close the thread with the $CLOSE_THREAD_TOOL_NAME tool.  It is very important to use the $SEND_MESSAGE_TOOL_NAME 
tool to communicate in these threads as no other agent will see your messages otherwise!  If you have sent a message 
and expect or require a response from another agent, use the $WAIT_FOR_MENTIONS_TOOL_NAME tool to wait for a response.
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
        uri = INSTRUCTION_RESOURCE_URI,
        mimeType = "text/markdown",
        readHandler = ::handle,
    )
}
