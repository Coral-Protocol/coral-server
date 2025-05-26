package org.coralprotocol.coralserver.session

import kotlinx.coroutines.*
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.ImmutableThread
import org.coralprotocol.coralserver.models.Message
import org.coralprotocol.coralserver.models.Thread
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

/**
 * Session class to hold stateful information for a specific application and privacy key.
 * This is now a compatibility wrapper around the thread-safe CoralSessionStateManager.
 * [devRequiredAgentStartCount] is the number of agents that need to register before the session can proceed. This is for devmode only.
 */
class CoralAgentGraphSession(
    val id: String,
    val applicationId: String,
    val privacyKey: String,
    val coralAgentConnections: MutableList<CoralAgentIndividualMcp> = mutableListOf(),
    val groups: List<Set<String>> = listOf(),
    var devRequiredAgentStartCount: Int = 0,
) {
    // Delegate to thread-safe state manager
    private val stateManager = CoralSessionStateManager(
        id = id,
        applicationId = applicationId,
        privacyKey = privacyKey,
        groups = groups,
        devRequiredAgentStartCount = devRequiredAgentStartCount
    )

    fun getAllThreadsAgentParticipatesIn(agentId: String): List<Thread> {
        return stateManager.getAllThreadsAgentParticipatesIn(agentId).map { it.toMutableThread() }
    }

    fun getThreads(): List<Thread> {
        return stateManager.getThreads().map { it.toMutableThread() }
    }

    fun clearAll() {
        stateManager.clearAll()
    }

    fun registerAgent(agent: Agent): Boolean {
        return runBlocking {
            val currentState = stateManager.state.value
            if (currentState.agents.containsKey(agent.id)) {
                false
            } else {
                stateManager.dispatch(SessionCommand.RegisterAgent(agent))
                true
            }
        }
    }

    fun getRegisteredAgentsCount(): Int = stateManager.getRegisteredAgentsCount()

    suspend fun waitForGroup(agentId: String, timeoutMs: Long): Boolean =
        stateManager.waitForGroup(agentId, timeoutMs)

    suspend fun waitForAgentCount(targetCount: Int, timeoutMs: Long): Boolean = 
        stateManager.waitForAgentCount(targetCount, timeoutMs)

    fun getAgent(agentId: String): Agent? = stateManager.getAgent(agentId)

    fun getAllAgents(): List<Agent> = stateManager.getAllAgents()

    fun createThread(name: String, creatorId: String, participantIds: List<String>): Thread {
        return runBlocking {
            val currentState = stateManager.state.value
            val creator = currentState.agents[creatorId] 
                ?: throw IllegalArgumentException("Creator agent not found")

            val validParticipants = participantIds.filter { currentState.agents.containsKey(it) }.toSet()

            stateManager.dispatch(
                SessionCommand.CreateThread(name, creatorId, validParticipants)
            )
            
            // Wait for the thread to be created
            delay(10) // Small delay to ensure state is updated
            val thread = stateManager.state.value.threads.values
                .find { it.name == name && it.creatorId == creatorId }
                ?: throw IllegalStateException("Thread creation failed")
                
            thread.toMutableThread()
        }
    }

    fun getThread(threadId: String): Thread? = stateManager.getThread(threadId)?.toMutableThread()

    fun getThreadsForAgent(agentId: String): List<Thread> {
        return stateManager.getThreadsForAgent(agentId).map { it.toMutableThread() }
    }

    fun addParticipantToThread(threadId: String, participantId: String): Boolean {
        return runBlocking {
            val currentState = stateManager.state.value
            val thread = currentState.threads[threadId] ?: return@runBlocking false
            val agent = currentState.agents[participantId] ?: return@runBlocking false

            if (thread.isClosed) return@runBlocking false
            if (thread.participants.contains(participantId)) return@runBlocking true

            stateManager.dispatch(
                SessionCommand.AddParticipant(threadId, participantId)
            )
            true
        }
    }

    fun removeParticipantFromThread(threadId: String, participantId: String): Boolean {
        return runBlocking {
            val currentState = stateManager.state.value
            val thread = currentState.threads[threadId] ?: return@runBlocking false

            if (thread.isClosed) return@runBlocking false
            if (!thread.participants.contains(participantId)) return@runBlocking false

            stateManager.dispatch(
                SessionCommand.RemoveParticipant(threadId, participantId)
            )
            true
        }
    }

    fun closeThread(threadId: String, summary: String): Boolean {
        return runBlocking {
            val currentState = stateManager.state.value
            val thread = currentState.threads[threadId] ?: return@runBlocking false

            if (thread.isClosed) return@runBlocking false

            stateManager.dispatch(
                SessionCommand.CloseThread(threadId, summary)
            )
            true
        }
    }

    fun getColorForSenderId(senderId: String): String {
        return stateManager.getColorForSenderId(senderId)
    }

    fun sendMessage(
        threadId: String,
        senderId: String,
        content: String,
        mentions: List<String> = emptyList()
    ): Message {
        return runBlocking {
            val currentState = stateManager.state.value
            val thread = currentState.threads[threadId] 
                ?: throw IllegalArgumentException("Thread with id $threadId not found")
            val sender = currentState.agents[senderId] 
                ?: throw IllegalArgumentException("Agent with id $senderId not found")

            stateManager.dispatch(
                SessionCommand.SendMessage(threadId, senderId, content, mentions)
            )
            
            // Wait for the message to be sent and return it
            delay(10) // Small delay to ensure state is updated
            val updatedThread = stateManager.state.value.threads[threadId]!!
            updatedThread.messages.last()
        }
    }

    // Notification is now handled internally by the state manager

    suspend fun waitForMentions(agentId: String, timeoutMs: Long): List<Message> {
        return stateManager.waitForMentions(agentId, timeoutMs)
    }

    fun getUnreadMessagesForAgent(agentId: String): List<Message> {
        return stateManager.getUnreadMessagesForAgent(agentId)
    }

    // Last read indices are now managed internally by the state manager
}

// Extension function to convert ImmutableThread to mutable Thread for compatibility
private fun ImmutableThread.toMutableThread(): Thread {
    return Thread(
        id = id,
        name = name,
        creatorId = creatorId,
        participants = participants.toMutableList(),
        messages = messages.toMutableList(),
        isClosed = isClosed,
        summary = summary
    )
}
