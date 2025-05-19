package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.Message
import org.coralprotocol.coralserver.models.Thread
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

/**
 * Session class to hold stateful information for a specific application and privacy key.
 * [devRequiredAgentStartCount] is the number of agents that need to register before the session can proceed. This is for devmode only.
 * TODO: Implement a mechanism for waiting for specific agents to register for production mode.
 */
class CoralAgentGraphSession(
    val id: String,
    val applicationId: String,
    val privacyKey: String,
    val coralAgentConnections: MutableList<CoralAgentIndividualMcp> = mutableListOf(),
    var devRequiredAgentStartCount: Int = 0
) {
    private val agents = ConcurrentHashMap<String, Agent>()

    private val threads = ConcurrentHashMap<String, Thread>()

    private val agentNotifications = ConcurrentHashMap<String, CompletableDeferred<List<Message>>>()

    private val lastReadMessageIndex = ConcurrentHashMap<Pair<String, String>, Int>()

    @OptIn(ExperimentalAtomicApi::class)
    private var registeredAgentsCount = AtomicInt(0)

    val agentCountNotifications = ConcurrentHashMap<Int, MutableList<CompletableDeferred<Boolean>>>()

    fun getAllThreadsAgentParticipatesIn(agentId: String): List<Thread> {
        return threads.values.filter { it.participants.contains(agentId) }
    }

    fun getThreads(): List<Thread> {
        return threads.values.toList()
    }

    fun clearAll() {
        agents.clear()
        threads.clear()
        agentNotifications.clear()
        lastReadMessageIndex.clear()
//        registeredAgentsCount = 0
        agentCountNotifications.clear()
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun registerAgent(agent: Agent): Boolean {
        if (agents.containsKey(agent.id)) {
            return false
        }
        agents[agent.id] = agent

        registeredAgentsCount.incrementAndFetch()

        // Create a copy of the keys to avoid ConcurrentModificationException
        val targetCounts = agentCountNotifications.keys.toList()

//        // For each target count that has been reached
        for (targetCount in targetCounts) {
            if (registeredAgentsCount.load() >= targetCount) {
                // Get the list of deferreds for this target count
                val deferreds = agentCountNotifications[targetCount]
                if (deferreds != null) {
                    // Complete all deferreds that are not already completed
                    for (deferred in deferreds) {
                        if (!deferred.isCompleted) {
                            deferred.complete(true)
                        }
                    }
                    // Remove this target count from the map
                    agentCountNotifications.remove(targetCount)
                }
            }
        }

        return true
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun getRegisteredAgentsCount(): Int {
        return registeredAgentsCount.load()
    }

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun waitForAgentCount(targetCount: Int, timeoutMs: Long): Boolean {
        if (registeredAgentsCount.load() >= targetCount) {
            return true
        }

        val deferred = CompletableDeferred<Boolean>()

        // Get or create the list of deferreds for this target count
        val deferreds = agentCountNotifications.computeIfAbsent(targetCount) { mutableListOf() }

        // Add the new deferred to the list
        deferreds.add(deferred)

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: false

        if (!result) {
            // If the wait timed out, remove this deferred from the list
            val deferredsList = agentCountNotifications[targetCount]
            if (deferredsList != null) {
                deferredsList.remove(deferred)
                // If the list is now empty, remove the target count from the map
                if (deferredsList.isEmpty()) {
                    agentCountNotifications.remove(targetCount)
                }
            }
        }

        return result
    }

    fun getAgent(agentId: String): Agent? = agents[agentId]

    fun getAllAgents(): List<Agent> = agents.values.toList()

    fun createThread(name: String, creatorId: String, participantIds: List<String>): Thread {
        val creator = agents[creatorId] ?: throw IllegalArgumentException("Creator agent not found")

        val validParticipants = participantIds.filter { agents.containsKey(it) }.toMutableList()

        if (!validParticipants.contains(creatorId)) {
            validParticipants.add(creatorId)
        }

        val thread = Thread(
            name = name,
            creatorId = creatorId,
            participants = validParticipants
        )
        threads[thread.id] = thread
        return thread
    }

    fun getThread(threadId: String): Thread? = threads[threadId]

    fun getThreadsForAgent(agentId: String): List<Thread> {
        return threads.values.filter { it.participants.contains(agentId) }
    }

    fun addParticipantToThread(threadId: String, participantId: String): Boolean {
        val thread = threads[threadId] ?: return false
        val agent = agents[participantId] ?: return false

        if (thread.isClosed) return false

        if (!thread.participants.contains(participantId)) {
            thread.participants.add(participantId)
            lastReadMessageIndex[Pair(participantId, threadId)] = thread.messages.size
        }
        return true
    }

    fun removeParticipantFromThread(threadId: String, participantId: String): Boolean {
        val thread = threads[threadId] ?: return false

        if (thread.isClosed) return false

        return thread.participants.remove(participantId)
    }

    fun closeThread(threadId: String, summary: String): Boolean {
        val thread = threads[threadId] ?: return false

        thread.isClosed = true
        thread.summary = summary

        return true
    }

    fun getColorForSenderId(senderId: String): String {
        val colors = listOf(
            "#FF5733", "#33FF57", "#3357FF", "#F3FF33", "#FF33F3",
            "#33FFF3", "#FF8033", "#8033FF", "#33FF80", "#FF3380"
        )
        val hash = senderId.hashCode()
        val index = Math.abs(hash) % colors.size
        return colors[index]
    }

    fun sendMessage(threadId: String, senderId: String, content: String, mentions: List<String> = emptyList()): Message {
        val thread = getThread(threadId) ?: throw IllegalArgumentException("Thread with id $threadId not found")
        val sender = getAgent(senderId) ?: throw IllegalArgumentException("Agent with id $senderId not found")

        val message = Message.create(thread, sender, content, mentions)
        thread.messages.add(message)
        notifyMentionedAgents(message)
        return message
    }

    private fun notifyMentionedAgents(message: Message) {
        if (message.sender.id == "system") {
            val thread = threads[message.thread.id] ?: return
            thread.participants.forEach { participantId ->
                val deferred = agentNotifications[participantId]
                if (deferred != null && !deferred.isCompleted) {
                    deferred.complete(listOf(message))
                }
            }
            return
        }

        message.mentions.forEach { mentionId ->
            val deferred = agentNotifications[mentionId]
            if (deferred != null && !deferred.isCompleted) {
                deferred.complete(listOf(message))
            }
        }
    }

    suspend fun waitForMentions(agentId: String, timeoutMs: Long): List<Message> {
        if(timeoutMs <= 0) {
            throw IllegalArgumentException("Timeout must be greater than 0")
        }

        val agent = agents[agentId] ?: return emptyList()

        val unreadMessages = getUnreadMessagesForAgent(agentId)
        if (unreadMessages.isNotEmpty()) {
            updateLastReadIndices(agentId, unreadMessages)
            return unreadMessages
        }

        val deferred = CompletableDeferred<List<Message>>()
        agentNotifications[agentId] = deferred

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: emptyList()

        agentNotifications.remove(agentId)

        updateLastReadIndices(agentId, result)

        return result
    }

    fun getUnreadMessagesForAgent(agentId: String): List<Message> {
        val agent = agents[agentId] ?: return emptyList()

        val result = mutableListOf<Message>()

        val agentThreads = getThreadsForAgent(agentId)

        for (thread in agentThreads) {
            val lastReadIndex = lastReadMessageIndex[Pair(agentId, thread.id)] ?: 0

            val unreadMessages = thread.messages.subList(lastReadIndex, thread.messages.size)

            result.addAll(unreadMessages.filter {
                it.mentions.contains(agentId) || it.sender.id == "system"
            })
        }

        return result
    }

    fun updateLastReadIndices(agentId: String, messages: List<Message>) {
        val messagesByThread = messages.groupBy { it.thread }

        for ((thread, threadMessages) in messagesByThread) {
            val messageIndices = threadMessages.map { thread.messages.indexOf(it) }
            if (messageIndices.isNotEmpty()) {
                val maxIndex = messageIndices.maxOrNull() ?: continue
                lastReadMessageIndex[Pair(agentId, thread.id)] = maxIndex + 1
            }
        }
    }
}
