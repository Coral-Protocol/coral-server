package org.coralprotocol.coralserver.mcp.tools

import org.coralprotocol.coralserver.mcp.addMcpTool
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

/**
 * Extension function to add all thread-based tools to a server.
 */
fun CoralAgentIndividualMcp.addThreadTools() {
    addMcpTool(AddParticipantTool())
    addMcpTool(CloseThreadTool())
    addMcpTool(ListAgentsTool())
    addMcpTool(CreateThreadTool())
    addMcpTool(RemoveParticipantTool())
    addMcpTool(SendMessageTool())
    addMcpTool(WaitForMentionsTool())
}