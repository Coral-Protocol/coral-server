package org.coralprotocol.coralserver.mcp.tools.models

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.session.ThreadId

/**
 * Tool for creating a new thread.
 */
@Serializable
data class CreateThreadInput(
    val threadName: String,
    val participantIds: Set<UniqueAgentName>
)

/**
 * Tool for adding a participant to a thread.
 */
@Serializable
data class AddParticipantInput(
    val threadId: ThreadId,
    val participantId: UniqueAgentName
)

/**
 * Tool for removing a participant from a thread.
 */
@Serializable
data class RemoveParticipantInput(
    val threadId: ThreadId,
    val participantId: UniqueAgentName
)

/**
 * Tool for closing a thread with a summary.
 */
@Serializable
data class CloseThreadInput(
    val threadId: ThreadId,
    val summary: String
)

/**
 * Tool for sending a message to a thread.
 */
@Serializable
data class SendMessageInput(
    val threadId: ThreadId,
    val content: String,
    val mentions: List<UniqueAgentName> = emptyList()
)

/**
 * Tool for waiting for new messages mentioning an agent.
 */
@Serializable
data class WaitForMentionsInput(
    val timeoutMs: Long = 30000
)

/**
 * Tool for listing all registered agents.
 */
@Serializable
data class ListAgentsInput(
    val includeDetails: Boolean = true // Whether to include agent details in the response
)

/**
 * Input for CloseSessionTool
 */
@Serializable
data class CloseSessionToolInput(
    val reason: String
)