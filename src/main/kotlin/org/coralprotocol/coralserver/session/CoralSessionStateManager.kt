package org.coralprotocol.coralserver.session

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.coralprotocol.coralserver.models.*
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe session state manager using event-driven architecture.
 * All state modifications go through a single command processor to ensure consistency.
 */
class CoralSessionStateManager(
    val id: String,
    val applicationId: String,
    val privacyKey: String,
    val groups: List<Set<String>> = listOf(),
    var devRequiredAgentStartCount: Int = 0,
    private val scope: CoroutineScope = GlobalScope
) {
    // Reactive state
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()
    
    // Event stream for observers
    private val _events = MutableSharedFlow<SessionEvent>(
        replay = 100,  // Keep last 100 events for late subscribers
        extraBufferCapacity = 1000
    )
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()
    
    // Command processing channel
    private val commandChannel = Channel<SessionCommand>(Channel.UNLIMITED)
    
    // Agent connections (kept for compatibility)
    val coralAgentConnections: MutableList<CoralAgentIndividualMcp> = mutableListOf()
    
    // Schedulers for group coordination
    private val agentGroupScheduler = GroupScheduler(groups)
    private val countBasedScheduler = CountBasedScheduler()
    
    // Agent notification channels
    private val agentNotificationChannels = ConcurrentHashMap<String, Channel<Message>>()
    
    init {
        // Start command processor
        scope.launch {
            for (command in commandChannel) {
                try {
                    processCommand(command)
                } catch (e: Exception) {
                    // Log error but continue processing
                    e.printStackTrace()
                }
            }
        }
        
        // Start notification processor
        scope.launch {
            events.collect { event ->
                when (event) {
                    is SessionEvent.MessageSent -> handleMessageNotifications(event)
                    else -> { /* Other events don't need notifications */ }
                }
            }
        }
    }
    
    /**
     * Dispatch a command to be processed.
     */
    suspend fun dispatch(command: SessionCommand) {
        commandChannel.send(command)
    }
    
    /**
     * Process a command and emit resulting events.
     */
    private suspend fun processCommand(command: SessionCommand) {
        val currentState = _state.value
        val event = when (command) {
            is SessionCommand.RegisterAgent -> {
                if (currentState.agents.containsKey(command.agent.id)) {
                    null // Agent already registered
                } else {
                    // Register with schedulers
                    agentGroupScheduler.registerAgent(command.agent.id)
                    countBasedScheduler.registerAgent(command.agent.id)
                    SessionEvent.AgentRegistered(command.agent)
                }
            }
            
            is SessionCommand.CreateThread -> {
                val creator = currentState.agents[command.creatorId]
                if (creator == null) {
                    null // Creator not found
                } else {
                    val validParticipants = command.participants
                        .filter { currentState.agents.containsKey(it) }
                        .toSet() + command.creatorId
                    
                    val thread = ImmutableThread(
                        name = command.name,
                        creatorId = command.creatorId,
                        participants = validParticipants
                    )
                    SessionEvent.ThreadCreated(thread)
                }
            }
            
            is SessionCommand.SendMessage -> {
                val thread = currentState.threads[command.threadId]
                val sender = currentState.agents[command.senderId]
                
                if (thread == null || sender == null || thread.isClosed) {
                    null // Invalid state
                } else {
                    val message = Message.create(
                        thread = Thread(
                            id = thread.id,
                            name = thread.name,
                            creatorId = thread.creatorId,
                            participants = thread.participants.toMutableList(),
                            messages = thread.messages.toMutableList()
                        ),
                        sender = sender,
                        content = command.content,
                        mentions = command.mentions
                    )
                    SessionEvent.MessageSent(command.threadId, message)
                }
            }
            
            is SessionCommand.AddParticipant -> {
                val thread = currentState.threads[command.threadId]
                val participant = currentState.agents[command.participantId]
                
                if (thread == null || participant == null || thread.isClosed) {
                    null
                } else if (thread.participants.contains(command.participantId)) {
                    null // Already a participant
                } else {
                    SessionEvent.ParticipantAdded(command.threadId, command.participantId)
                }
            }
            
            is SessionCommand.RemoveParticipant -> {
                val thread = currentState.threads[command.threadId]
                
                if (thread == null || thread.isClosed) {
                    null
                } else if (!thread.participants.contains(command.participantId)) {
                    null // Not a participant
                } else {
                    SessionEvent.ParticipantRemoved(command.threadId, command.participantId)
                }
            }
            
            is SessionCommand.CloseThread -> {
                val thread = currentState.threads[command.threadId]
                
                if (thread == null || thread.isClosed) {
                    null
                } else {
                    SessionEvent.ThreadClosed(command.threadId, command.summary)
                }
            }
        }
        
        // Emit event and update state
        event?.let {
            _events.emit(it)
            _state.update { state -> applyEvent(state, it) }
        }
    }
    
    /**
     * Apply an event to the current state to produce a new state.
     */
    private fun applyEvent(state: SessionState, event: SessionEvent): SessionState {
        return when (event) {
            is SessionEvent.AgentRegistered -> {
                state.copy(agents = state.agents + (event.agent.id to event.agent))
            }
            
            is SessionEvent.ThreadCreated -> {
                val newState = state.copy(threads = state.threads + (event.thread.id to event.thread))
                // Initialize read indices for all participants
                val newIndices = event.thread.participants.associateBy(
                    { Pair(it, event.thread.id) },
                    { 0 }
                )
                newState.copy(lastReadIndices = state.lastReadIndices + newIndices)
            }
            
            is SessionEvent.MessageSent -> {
                state.copy(
                    threads = state.threads.mapValues { (id, thread) ->
                        if (id == event.threadId) {
                            thread.addMessage(event.message)
                        } else {
                            thread
                        }
                    }
                )
            }
            
            is SessionEvent.ParticipantAdded -> {
                val newState = state.copy(
                    threads = state.threads.mapValues { (id, thread) ->
                        if (id == event.threadId) {
                            thread.addParticipant(event.participantId)
                        } else {
                            thread
                        }
                    }
                )
                // Initialize read index for new participant
                val currentThread = newState.threads[event.threadId]
                if (currentThread != null) {
                    newState.copy(
                        lastReadIndices = newState.lastReadIndices + 
                            (Pair(event.participantId, event.threadId) to currentThread.messages.size)
                    )
                } else {
                    newState
                }
            }
            
            is SessionEvent.ParticipantRemoved -> {
                state.copy(
                    threads = state.threads.mapValues { (id, thread) ->
                        if (id == event.threadId) {
                            thread.removeParticipant(event.participantId)
                        } else {
                            thread
                        }
                    }
                )
            }
            
            is SessionEvent.ThreadClosed -> {
                state.copy(
                    threads = state.threads.mapValues { (id, thread) ->
                        if (id == event.threadId) {
                            thread.close(event.summary)
                        } else {
                            thread
                        }
                    }
                )
            }
        }
    }
    
    /**
     * Handle message notifications for mentioned agents.
     */
    private suspend fun handleMessageNotifications(event: SessionEvent.MessageSent) {
        val message = event.message
        val thread = _state.value.threads[event.threadId] ?: return
        
        if (message.sender.id == "system") {
            // Notify all participants for system messages
            thread.participants.forEach { participantId ->
                agentNotificationChannels[participantId]?.send(message)
            }
        } else {
            // Notify mentioned agents
            message.mentions.forEach { mentionId ->
                agentNotificationChannels[mentionId]?.send(message)
            }
        }
    }
    
    /**
     * Wait for mentions for a specific agent.
     */
    suspend fun waitForMentions(agentId: String, timeoutMs: Long): List<Message> {
        if (timeoutMs <= 0) {
            throw IllegalArgumentException("Timeout must be greater than 0")
        }
        
        val currentState = _state.value
        if (!currentState.agents.containsKey(agentId)) {
            return emptyList()
        }
        
        // Check for unread messages first
        val unreadMessages = getUnreadMessagesForAgent(agentId)
        if (unreadMessages.isNotEmpty()) {
            updateLastReadIndices(agentId, unreadMessages)
            return unreadMessages
        }
        
        // Wait for new messages
        val channel = agentNotificationChannels.computeIfAbsent(agentId) {
            Channel(Channel.UNLIMITED)
        }
        
        val messages = withTimeoutOrNull(timeoutMs) {
            val result = mutableListOf<Message>()
            // Collect all available messages
            while (!channel.isEmpty || result.isEmpty()) {
                result.add(channel.receive())
            }
            result
        } ?: emptyList()
        
        if (messages.isNotEmpty()) {
            updateLastReadIndices(agentId, messages)
        }
        
        return messages
    }
    
    /**
     * Get unread messages for an agent.
     */
    fun getUnreadMessagesForAgent(agentId: String): List<Message> {
        val currentState = _state.value
        if (!currentState.agents.containsKey(agentId)) {
            return emptyList()
        }
        
        val result = mutableListOf<Message>()
        val agentThreads = currentState.getThreadsForAgent(agentId)
        
        for (thread in agentThreads) {
            val lastReadIndex = currentState.getLastReadIndex(agentId, thread.id)
            val unreadMessages = thread.messages.drop(lastReadIndex)
            
            result.addAll(unreadMessages.filter {
                it.mentions.contains(agentId) || it.sender.id == "system"
            })
        }
        
        return result
    }
    
    /**
     * Update last read indices for an agent.
     */
    private suspend fun updateLastReadIndices(agentId: String, messages: List<Message>) {
        val currentState = _state.value
        val messagesByThread = messages.groupBy { it.thread.id }
        
        val updates = mutableMapOf<Pair<String, String>, Int>()
        
        for ((threadId, threadMessages) in messagesByThread) {
            val thread = currentState.threads[threadId] ?: continue
            val messageIndices = threadMessages.mapNotNull { msg ->
                thread.messages.indexOfFirst { it.id == msg.id }
            }.filter { it >= 0 }
            
            if (messageIndices.isNotEmpty()) {
                val maxIndex = messageIndices.maxOrNull() ?: continue
                updates[Pair(agentId, threadId)] = maxIndex + 1
            }
        }
        
        if (updates.isNotEmpty()) {
            _state.update { state ->
                state.copy(lastReadIndices = state.lastReadIndices + updates)
            }
        }
    }
    
    // Compatibility methods
    fun getAllThreadsAgentParticipatesIn(agentId: String): List<ImmutableThread> {
        return _state.value.getThreadsForAgent(agentId)
    }
    
    fun getThreads(): List<ImmutableThread> {
        return _state.value.threads.values.toList()
    }
    
    fun getThread(threadId: String): ImmutableThread? {
        return _state.value.getThread(threadId)
    }
    
    fun getThreadsForAgent(agentId: String): List<ImmutableThread> {
        return _state.value.getThreadsForAgent(agentId)
    }
    
    fun getAgent(agentId: String): Agent? {
        return _state.value.getAgent(agentId)
    }
    
    fun getAllAgents(): List<Agent> {
        return _state.value.getAllAgents()
    }
    
    fun getRegisteredAgentsCount(): Int {
        return countBasedScheduler.getRegisteredAgentsCount()
    }
    
    suspend fun waitForGroup(agentId: String, timeoutMs: Long): Boolean {
        return agentGroupScheduler.waitForGroup(agentId, timeoutMs)
    }
    
    suspend fun waitForAgentCount(targetCount: Int, timeoutMs: Long): Boolean {
        return countBasedScheduler.waitForAgentCount(targetCount, timeoutMs)
    }
    
    fun clearAll() {
        _state.value = SessionState()
        agentNotificationChannels.clear()
        countBasedScheduler.clear()
        agentGroupScheduler.clear()
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
}