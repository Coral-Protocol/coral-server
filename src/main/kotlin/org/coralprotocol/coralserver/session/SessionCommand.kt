package org.coralprotocol.coralserver.session

import org.coralprotocol.coralserver.models.Agent

/**
 * Commands represent intentions to modify session state.
 * All commands are processed sequentially to ensure consistency.
 */
sealed class SessionCommand {
    data class RegisterAgent(val agent: Agent) : SessionCommand()
    
    data class CreateThread(
        val name: String,
        val creatorId: String,
        val participants: Set<String>
    ) : SessionCommand()
    
    data class SendMessage(
        val threadId: String,
        val senderId: String,
        val content: String,
        val mentions: List<String>
    ) : SessionCommand()
    
    data class AddParticipant(
        val threadId: String,
        val participantId: String
    ) : SessionCommand()
    
    data class RemoveParticipant(
        val threadId: String,
        val participantId: String
    ) : SessionCommand()
    
    data class CloseThread(
        val threadId: String,
        val summary: String
    ) : SessionCommand()
}