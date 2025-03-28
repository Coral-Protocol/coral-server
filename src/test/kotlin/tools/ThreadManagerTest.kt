package org.coralprotocol.agentfuzzyp2ptools.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.agentfuzzyp2ptools.Agent
import org.coralprotocol.agentfuzzyp2ptools.ThreadManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ThreadManagerTest {

    @BeforeEach
    fun setup() {
        // Clear any existing data before each test
        ThreadManager.clearAll()
    }

    @Test
    fun `test agent registration`() {
        // Register a new agent
        val agent = Agent(id = "agent1", name = "Test Agent 1")
        val success = ThreadManager.registerAgent(agent)

        // Verify agent was registered
        assertTrue(success)
        assertEquals(agent, ThreadManager.getAgent("agent1"))

        // Try to register the same agent again
        val duplicateSuccess = ThreadManager.registerAgent(agent)
        assertFalse(duplicateSuccess)
    }

    @Test
    fun `test agent registration with description`() {
        // Register a new agent with description
        val agent = Agent(id = "agent2", name = "Test Agent 2", description = "This agent is responsible for testing")
        val success = ThreadManager.registerAgent(agent)

        // Verify agent was registered with description
        assertTrue(success)
        val retrievedAgent = ThreadManager.getAgent("agent2")
        assertEquals(agent, retrievedAgent)
        assertEquals("This agent is responsible for testing", retrievedAgent?.description)
    }

    @Test
    fun `test thread creation`() {
        // Register agents
        val creator = Agent(id = "creator", name = "Creator Agent")
        val participant1 = Agent(id = "participant1", name = "Participant 1")
        val participant2 = Agent(id = "participant2", name = "Participant 2")

        ThreadManager.registerAgent(creator)
        ThreadManager.registerAgent(participant1)
        ThreadManager.registerAgent(participant2)

        // Create a thread
        val thread = ThreadManager.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant1", "participant2")
        )

        // Verify thread was created
        assertNotNull(thread)
        assertEquals("Test Thread", thread?.name)
        assertEquals("creator", thread?.creatorId)
        assertTrue(thread?.participants?.contains("creator") ?: false)
        assertTrue(thread?.participants?.contains("participant1") ?: false)
        assertTrue(thread?.participants?.contains("participant2") ?: false)
        assertEquals(3, thread?.participants?.size)
    }

    @Test
    fun `test adding and removing participants`() {
        // Register agents
        val creator = Agent(id = "creator", name = "Creator Agent")
        val participant1 = Agent(id = "participant1", name = "Participant 1")
        val participant2 = Agent(id = "participant2", name = "Participant 2")
        val participant3 = Agent(id = "participant3", name = "Participant 3")

        ThreadManager.registerAgent(creator)
        ThreadManager.registerAgent(participant1)
        ThreadManager.registerAgent(participant2)
        ThreadManager.registerAgent(participant3)

        // Create a thread
        val thread = ThreadManager.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant1")
        )

        // Add a participant
        val addSuccess = ThreadManager.addParticipant(
            threadId = thread?.id ?: "",
            participantId = "participant2"
        )

        // Verify participant was added
        assertTrue(addSuccess)
        val updatedThread = ThreadManager.getThread(thread?.id ?: "")
        assertTrue(updatedThread?.participants?.contains("participant2") ?: false)

        // Remove a participant
        val removeSuccess = ThreadManager.removeParticipant(
            threadId = thread?.id ?: "",
            participantId = "participant1"
        )

        // Verify participant was removed
        assertTrue(removeSuccess)
        val finalThread = ThreadManager.getThread(thread?.id ?: "")
        assertFalse(finalThread?.participants?.contains("participant1") ?: true)
    }

    @Test
    fun `test sending messages and closing thread`() {
        // Register agents
        val creator = Agent(id = "creator", name = "Creator Agent")
        val participant = Agent(id = "participant", name = "Participant")

        ThreadManager.registerAgent(creator)
        ThreadManager.registerAgent(participant)

        // Create a thread
        val thread = ThreadManager.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant")
        )

        // Send a message
        val message = ThreadManager.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "creator",
            content = "Hello, world!",
            mentions = listOf("participant")
        )

        // Verify message was sent
        assertNotNull(message)
        assertEquals("Hello, world!", message?.content)
        assertEquals("creator", message?.senderId)
        assertEquals(thread?.id, message?.threadId)
        assertTrue(message?.mentions?.contains("participant") ?: false)

        // Close the thread
        val closeSuccess = ThreadManager.closeThread(
            threadId = thread?.id ?: "",
            summary = "Thread completed"
        )

        // Verify thread was closed
        assertTrue(closeSuccess)
        val closedThread = ThreadManager.getThread(thread?.id ?: "")
        assertTrue(closedThread?.isClosed ?: false)
        assertEquals("Thread completed", closedThread?.summary)

        // Try to send a message to a closed thread
        val failedMessage = ThreadManager.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "creator",
            content = "This should fail",
            mentions = listOf()
        )

        // Verify message was not sent
        assertNull(failedMessage)
    }

    @Test
    fun `test waiting for mentions`() = runBlocking {
        // Register agents
        val creator = Agent(id = "creator", name = "Creator Agent")
        val participant = Agent(id = "participant", name = "Participant")

        ThreadManager.registerAgent(creator)
        ThreadManager.registerAgent(participant)

        // Create a thread
        val thread = ThreadManager.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant")
        )

        // Launch a coroutine to wait for mentions
        val waitJob = launch(Dispatchers.Default) {
            val messages = ThreadManager.waitForMentions(
                agentId = "participant",
                timeoutMs = 5000
            )

            // Verify messages were received
            assertFalse(messages.isEmpty())
            assertEquals(1, messages.size)
            assertEquals("Hello, participant!", messages[0].content)
        }

        // Wait a bit to ensure the wait operation has started
        delay(100)

        // Send a message with a mention
        ThreadManager.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "creator",
            content = "Hello, participant!",
            mentions = listOf("participant")
        )

        // Wait for the job to complete
        waitJob.join()
    }

    @Test
    fun `test waiting for mentions with timeout`() = runBlocking {
        // Register an agent
        val agent = Agent(id = "agent", name = "Test Agent")
        ThreadManager.registerAgent(agent)

        // Wait for mentions with a short timeout
        val messages = ThreadManager.waitForMentions(
            agentId = "agent",
            timeoutMs = 100
        )

        // Verify no messages were received
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `test listing all agents`() {
        // Clear any existing agents
        // Note: In a real implementation, we would have a way to clear the agents for testing

        // Register multiple agents
        val agent1 = Agent(id = "agent1", name = "Agent 1")
        val agent2 = Agent(id = "agent2", name = "Agent 2")
        val agent3 = Agent(id = "agent3", name = "Agent 3")

        ThreadManager.registerAgent(agent1)
        ThreadManager.registerAgent(agent2)
        ThreadManager.registerAgent(agent3)

        // Get all agents
        val agents = ThreadManager.getAllAgents()

        // Verify all agents are returned
        assertEquals(3, agents.size)
        assertTrue(agents.contains(agent1))
        assertTrue(agents.contains(agent2))
        assertTrue(agents.contains(agent3))
    }
}
