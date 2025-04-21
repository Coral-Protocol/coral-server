package org.coralprotocol.coralserver.tools

import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * Extension function to add all thread-based tools to a server.
 */
fun Server.addThreadTools() {
    addRegisterAgentTool()
    addListAgentsTool()
    addCreateThreadTool()
    addAddParticipantTool()
    addRemoveParticipantTool()
    addCloseThreadTool()
    addSendMessageTool()
    addWaitForMentionsTool()
}