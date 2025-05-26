package org.coralprotocol.coralserver.session

import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.Message
import org.coralprotocol.coralserver.models.Thread

/**
 * Events represent facts about state changes that have occurred.
 * Events are immutable and can be replayed to reconstruct state.
 */
sealed class SessionEvent {
    data class AgentRegistered(val agent: Agent) : SessionEvent()
    
    data class ThreadCreated(val thread: Thread) : SessionEvent()
    
    data class MessageSent(
        val threadId: String,
        val message: Message
    ) : SessionEvent()
    
    data class ParticipantAdded(
        val threadId: String,
        val participantId: String
    ) : SessionEvent()
    
    data class ParticipantRemoved(
        val threadId: String,
        val participantId: String
    ) : SessionEvent()
    
    data class ThreadClosed(
        val threadId: String,
        val summary: String
    ) : SessionEvent()
}