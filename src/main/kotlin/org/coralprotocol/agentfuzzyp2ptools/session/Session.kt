package org.coralprotocol.agentfuzzyp2ptools.session

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.coralprotocol.agentfuzzyp2ptools.models.Agent
import org.coralprotocol.agentfuzzyp2ptools.models.Message
import org.coralprotocol.agentfuzzyp2ptools.models.Thread
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Session class to hold stateful information for a specific application and privacy key.
 */
class Session(
    val id: String,
    val applicationId: String,
    val privacyKey: String,
    val servers: MutableList<Server> = mutableListOf(),
    var waitForAgents: Int = 0
) {
    // Store registered agents
    private val agents = ConcurrentHashMap<String, Agent>()

    // Store active threads
    private val threads = ConcurrentHashMap<String, Thread>()

    // Store message notifications for agents
    private val agentNotifications = ConcurrentHashMap<String, CompletableDeferred<List<Message>>>()

    // Store last read message index for each agent in each thread
    private val lastReadMessageIndex = ConcurrentHashMap<Pair<String, String>, Int>()

    // Track the number of registered agents
    private val registeredAgentsCount = AtomicInteger(0)

    // Store agent count notifications
    private val agentCountNotifications = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    // Clear all data (for testing purposes)
    fun clearAll() {
        agents.clear()
        threads.clear()
        agentNotifications.clear()
        lastReadMessageIndex.clear()
        registeredAgentsCount.set(0)
        agentCountNotifications.clear()
    }

    // Register a new agent
    fun registerAgent(agent: Agent): Boolean {
        if (agents.containsKey(agent.id)) {
            return false
        }
        agents[agent.id] = agent

        // Increment the count of registered agents
        val newCount = registeredAgentsCount.incrementAndGet()

        // Notify any waiters that are waiting for this count or less
        agentCountNotifications.entries.removeIf { (targetCount, deferred) ->
            if (newCount >= targetCount && !deferred.isCompleted) {
                deferred.complete(true)
                true
            } else {
                false
            }
        }

        return true
    }

    // Get the current number of registered agents
    fun getRegisteredAgentsCount(): Int {
        return registeredAgentsCount.get()
    }

    // Wait for a specific number of agents to register
    suspend fun waitForAgentCount(targetCount: Int, timeoutMs: Long): Boolean {
        // If we already have enough agents, return immediately
        if (registeredAgentsCount.get() >= targetCount) {
            return true
        }

        // Otherwise, set up a deferred to wait for the target count
        val deferred = CompletableDeferred<Boolean>()
        agentCountNotifications[targetCount] = deferred

        // Wait for the deferred to complete or timeout
        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: false

        // Clean up if we timed out
        if (!result) {
            agentCountNotifications.remove(targetCount)
        }

        return result
    }

    // Get an agent by ID
    fun getAgent(agentId: String): Agent? = agents[agentId]

    // Get all registered agents
    fun getAllAgents(): List<Agent> = agents.values.toList()

    // Create a new thread
    fun createThread(name: String, creatorId: String, participantIds: List<String>): Thread? {
        // Verify creator exists
        val creator = agents[creatorId] ?: return null

        // Verify all participants exist
        val validParticipants = participantIds.filter { agents.containsKey(it) }.toMutableList()

        // Add creator to participants if not already included
        if (!validParticipants.contains(creatorId)) {
            validParticipants.add(creatorId)
        }

        // Create and store the thread
        val thread = Thread(
            name = name,
            creatorId = creatorId,
            participants = validParticipants
        )
        threads[thread.id] = thread
        return thread
    }

    // Get a thread by ID
    fun getThread(threadId: String): Thread? = threads[threadId]

    // Get all threads an agent is participating in
    fun getThreadsForAgent(agentId: String): List<Thread> {
        return threads.values.filter { it.participants.contains(agentId) }
    }

    // Add a participant to a thread
    fun addParticipant(threadId: String, participantId: String): Boolean {
        val thread = threads[threadId] ?: return false
        val agent = agents[participantId] ?: return false

        if (thread.isClosed) return false

        if (!thread.participants.contains(participantId)) {
            thread.participants.add(participantId)
            // Initialize last read message index for new participant
            lastReadMessageIndex[Pair(participantId, threadId)] = thread.messages.size
        }
        return true
    }

    // Remove a participant from a thread
    fun removeParticipant(threadId: String, participantId: String): Boolean {
        val thread = threads[threadId] ?: return false

        if (thread.isClosed) return false

        return thread.participants.remove(participantId)
    }

    // Close a thread with a summary
    fun closeThread(threadId: String, summary: String): Boolean {
        val thread = threads[threadId] ?: return false

        thread.isClosed = true
        thread.summary = summary

        // Notify all participants that the thread is closed
        val closeMessage = Message(
            threadId = threadId,
            senderId = "system",
            content = "Thread closed: $summary"
        )
        thread.messages.add(closeMessage)
        notifyMentionedAgents(closeMessage)

        return true
    }

    // Get color for sender ID (for UI display)
    fun getColorForSenderId(senderId: String): String {
        val colors = listOf(
            "#FF5733", "#33FF57", "#3357FF", "#F3FF33", "#FF33F3",
            "#33FFF3", "#FF8033", "#8033FF", "#33FF80", "#FF3380"
        )
        val hash = senderId.hashCode()
        val index = Math.abs(hash) % colors.size
        return colors[index]
    }

    // Send a message to a thread
    fun sendMessage(threadId: String, senderId: String, content: String, mentions: List<String> = emptyList()): Message? {
        val thread = threads[threadId] ?: return null
        val sender = agents[senderId] ?: return null

        if (thread.isClosed) return null

        if (!thread.participants.contains(senderId)) {
            return null
        }

        // Filter mentions to only include participants
        val validMentions = mentions.filter { thread.participants.contains(it) }

        // Create and store the message
        val message = Message(
            threadId = threadId,
            senderId = senderId,
            content = content,
            mentions = validMentions
        )
        thread.messages.add(message)

        // Notify mentioned agents
        notifyMentionedAgents(message)

        return message
    }

    // Notify mentioned agents about a new message
    fun notifyMentionedAgents(message: Message) {
        // Notify all participants if it's a system message
        if (message.senderId == "system") {
            val thread = threads[message.threadId] ?: return
            thread.participants.forEach { participantId ->
                val deferred = agentNotifications[participantId]
                if (deferred != null && !deferred.isCompleted) {
                    deferred.complete(listOf(message))
                }
            }
            return
        }

        // Otherwise, only notify mentioned agents
        message.mentions.forEach { mentionId ->
            val deferred = agentNotifications[mentionId]
            if (deferred != null && !deferred.isCompleted) {
                deferred.complete(listOf(message))
            }
        }
    }

    // Wait for mentions of an agent
    suspend fun waitForMentions(agentId: String, timeoutMs: Long): List<Message> {
        // Check if agent exists
        val agent = agents[agentId] ?: return emptyList()

        // Check if there are already unread messages mentioning this agent
        val unreadMessages = getUnreadMessagesForAgent(agentId)
        if (unreadMessages.isNotEmpty()) {
            // Mark these messages as read
            updateLastReadIndices(agentId, unreadMessages)
            return unreadMessages
        }

        // Otherwise, set up a deferred to wait for new mentions
        val deferred = CompletableDeferred<List<Message>>()
        agentNotifications[agentId] = deferred

        // Wait for the deferred to complete or timeout
        val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: emptyList()

        // Clean up
        agentNotifications.remove(agentId)

        // Mark these messages as read
        updateLastReadIndices(agentId, result)

        return result
    }

    // Get unread messages for an agent
    fun getUnreadMessagesForAgent(agentId: String): List<Message> {
        val agent = agents[agentId] ?: return emptyList()

        val result = mutableListOf<Message>()

        // Get all threads the agent is participating in
        val agentThreads = getThreadsForAgent(agentId)

        for (thread in agentThreads) {
            // Get the last read index for this agent in this thread
            val lastReadIndex = lastReadMessageIndex[Pair(agentId, thread.id)] ?: 0

            // Get all messages after the last read index
            val unreadMessages = thread.messages.subList(lastReadIndex, thread.messages.size)

            // Filter for messages that mention this agent or are system messages
            result.addAll(unreadMessages.filter { 
                it.mentions.contains(agentId) || it.senderId == "system" 
            })
        }

        return result
    }

    // Update last read indices for an agent
    fun updateLastReadIndices(agentId: String, messages: List<Message>) {
        val messagesByThread = messages.groupBy { it.threadId }

        for ((threadId, threadMessages) in messagesByThread) {
            val thread = threads[threadId] ?: continue
            val messageIndices = threadMessages.map { thread.messages.indexOf(it) }
            if (messageIndices.isNotEmpty()) {
                val maxIndex = messageIndices.maxOrNull() ?: continue
                lastReadMessageIndex[Pair(agentId, threadId)] = maxIndex + 1
            }
        }
    }
}

