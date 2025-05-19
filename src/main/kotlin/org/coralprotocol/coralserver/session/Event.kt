package org.coralprotocol.coralserver.session

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.Message
import org.coralprotocol.coralserver.models.Thread
import java.util.*

@Serializable
data class EventMessage(
    val id: String,
    val threadId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val mentions: List<String>,
)
fun Message.toEventMessage(): EventMessage = EventMessage(id = id, threadId = thread.id, senderId = sender.id, content = content, timestamp = timestamp, mentions = mentions)

@Serializable
sealed interface Event {
    @Serializable
    data class AgentRegistered(val agent: Agent) : Event
    @Serializable
    data class AgentReady(val agent: AgentName): Event
    @Serializable
    data class ThreadCreated(val id: String, val name: String, val creatorId: String, val participants: List<String>, val summary: String?): Event
    @Serializable
    data class MessageSent(val threadId: String, val message: EventMessage): Event
}