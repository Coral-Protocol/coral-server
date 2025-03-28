package org.coralprotocol.agentfuzzyp2ptools

import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Represents an agent in the system.
 */
@Serializable
data class Agent(
    val id: String,
    val name: String,
    val description: String = "" // Description of the agent's responsibilities
)

/**
 * Represents a message in a thread.
 */
@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mentions: List<String> = emptyList()
)

/**
 * Represents a thread with participants.
 */
@Serializable
data class Thread(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val creatorId: String,
    val participants: MutableList<String> = mutableListOf(),
    val messages: MutableList<Message> = mutableListOf(),
    var isClosed: Boolean = false,
    var summary: String? = null
)

/**
 * Singleton object to manage agents, threads, and messages.
 */
object ThreadManager {
    // Store registered agents
    private val agents = ConcurrentHashMap<String, Agent>()

    // Store active threads
    private val threads = ConcurrentHashMap<String, Thread>()

    // Store message notifications for agents
    private val agentNotifications = ConcurrentHashMap<String, CompletableDeferred<List<Message>>>()

    // Store last read message index for each agent in each thread
    private val lastReadMessageIndex = ConcurrentHashMap<Pair<String, String>, Int>()

    // Clear all data (for testing purposes)
    fun clearAll() {
        agents.clear()
        threads.clear()
        agentNotifications.clear()
        lastReadMessageIndex.clear()
    }

    // Register a new agent
    fun registerAgent(agent: Agent): Boolean {
        if (agents.containsKey(agent.id)) {
            return false
        }
        agents[agent.id] = agent
        return true
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

    // ANSI color codes for terminal output
    private val ANSI_COLORS = arrayOf(
        "\u001B[31m", // Red
        "\u001B[32m", // Green
        "\u001B[33m", // Yellow
        "\u001B[34m", // Blue
        "\u001B[35m", // Magenta
        "\u001B[36m", // Cyan
        "\u001B[91m", // Bright Red
        "\u001B[92m", // Bright Green
        "\u001B[93m", // Bright Yellow
        "\u001B[94m", // Bright Blue
        "\u001B[95m", // Bright Magenta
        "\u001B[96m"  // Bright Cyan
    )
    private val ANSI_RESET = "\u001B[0m"

    // Get color based on senderId hash
    private fun getColorForSenderId(senderId: String): String {
        // Calculate a simple hash of the senderId
        val hash = abs(senderId.hashCode())
        // Use the hash to select a color from the array
        return ANSI_COLORS[hash % ANSI_COLORS.size]
    }

    // Send a message to a thread
    fun sendMessage(threadId: String, senderId: String, content: String, mentions: List<String> = emptyList()): Message? {
        // Get color based on senderId
        val color = getColorForSenderId(senderId)

        // Print the message with color for the content
        println("($threadId) $senderId: $color$content$ANSI_RESET \n(mentions^: $mentions)")

        val thread = threads[threadId] ?: return null
        val sender = agents[senderId] ?: return null

        if (thread.isClosed) return null
        if (!thread.participants.contains(senderId)) return null

        // Create and store the message
        val message = Message(
            threadId = threadId,
            senderId = senderId,
            content = content,
            mentions = mentions.filter { agents.containsKey(it) && thread.participants.contains(it) }
        )
        thread.messages.add(message)

        // Notify mentioned agents
        notifyMentionedAgents(message)

        return message
    }

    // Notify agents mentioned in a message
    private fun notifyMentionedAgents(message: Message) {
        // Include all participants as they should receive the message
        val agentsToNotify = threads[message.threadId]?.participants ?: return

        for (agentId in agentsToNotify) {
            val deferred = agentNotifications[agentId]
            if (deferred != null && !deferred.isCompleted) {
                // Get unread messages for this agent
                val unreadMessages = getUnreadMessagesForAgent(agentId)
                if (unreadMessages.isNotEmpty()) {
                    deferred.complete(unreadMessages)
                }
            }
        }
    }

    // Wait for new messages mentioning an agent
    suspend fun waitForMentions(agentId: String, timeoutMs: Long): List<Message> {
        // Check if agent exists
        if (!agents.containsKey(agentId)) return emptyList()

        // Check if there are already unread messages
        val unreadMessages = getUnreadMessagesForAgent(agentId)
        if (unreadMessages.isNotEmpty()) {
            // Update last read message indices
            updateLastReadIndices(agentId, unreadMessages)
            return unreadMessages
        }

        // Create a deferred to wait for new messages
        val deferred = CompletableDeferred<List<Message>>()
        agentNotifications[agentId] = deferred

        // Wait with timeout
        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: emptyList()

        // Clean up if timed out
        if (!deferred.isCompleted) {
            deferred.complete(emptyList())
        }

        // Remove the notification
        agentNotifications.remove(agentId)

        // Update last read message indices
        updateLastReadIndices(agentId, result)

        return result
    }

    // Get unread messages for an agent across all threads they participate in
    private fun getUnreadMessagesForAgent(agentId: String): List<Message> {
        val unreadMessages = mutableListOf<Message>()

        // Get all threads the agent is participating in
        val agentThreads = getThreadsForAgent(agentId)

        for (thread in agentThreads) {
            val lastReadIndex = lastReadMessageIndex[Pair(agentId, thread.id)] ?: 0

            // Get messages after the last read index
            if (lastReadIndex < thread.messages.size) {
                unreadMessages.addAll(thread.messages.subList(lastReadIndex, thread.messages.size))
            }
        }

        return unreadMessages
    }

    // Update last read indices for an agent after receiving messages
    private fun updateLastReadIndices(agentId: String, messages: List<Message>) {
        // Group messages by thread ID
        val messagesByThread = messages.groupBy { it.threadId }

        // Update last read index for each thread
        for ((threadId, threadMessages) in messagesByThread) {
            val thread = threads[threadId] ?: continue
            lastReadMessageIndex[Pair(agentId, threadId)] = thread.messages.size
        }
    }
}
