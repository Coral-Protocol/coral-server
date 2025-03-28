package org.coralprotocol.agentfuzzyp2ptools.tools

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.coralprotocol.agentfuzzyp2ptools.Agent
import org.coralprotocol.agentfuzzyp2ptools.ThreadManager
import org.coralprotocol.agentfuzzyp2ptools.org.coralprotocol.agentfuzzyp2ptools.ThreadTools

class ThreadToolsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setup() {
        // Clear any existing data before each test
        ThreadManager.clearAll()
    }

    @Test
    fun `test formatMessagesAsXml`() {
        // Register agents
        val agent1 = Agent(id = "agent1", name = "Agent 1")
        val agent2 = Agent(id = "agent2", name = "Agent 2")
        ThreadManager.registerAgent(agent1)
        ThreadManager.registerAgent(agent2)

        // Create a thread
        val thread = ThreadManager.createThread(
            name = "Test Thread",
            creatorId = "agent1",
            participantIds = listOf("agent2")
        )

        // Send messages
        val message1 = ThreadManager.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "agent1",
            content = "Hello, agent2!",
            mentions = listOf("agent2")
        )

        val message2 = ThreadManager.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "agent2",
            content = "Hi, agent1!",
            mentions = listOf("agent1")
        )

        // Format messages as XML
        val xml = ThreadTools.formatMessagesAsXml(listOf(message1!!, message2!!))

        // Verify XML structure
        assertTrue(xml.contains("<messages>"))
        assertTrue(xml.contains("<thread id=\"${thread?.id}\" name=\"Test Thread\">"))
        assertTrue(xml.contains("<status>open</status>"))
        assertTrue(xml.contains("<participants>"))
        assertTrue(xml.contains("<participant id=\"agent1\" name=\"Agent 1\" />"))
        assertTrue(xml.contains("<participant id=\"agent2\" name=\"Agent 2\" />"))
        assertTrue(xml.contains("<messages>"))
        assertTrue(xml.contains("<message id=\"${message1.id}\""))
        assertTrue(xml.contains("<sender id=\"agent1\" name=\"Agent 1\" />"))
        assertTrue(xml.contains("<content>Hello, agent2!</content>"))
        assertTrue(xml.contains("<mentions>"))
        assertTrue(xml.contains("<mention id=\"agent2\" name=\"Agent 2\" />"))
        assertTrue(xml.contains("<message id=\"${message2.id}\""))
        assertTrue(xml.contains("<sender id=\"agent2\" name=\"Agent 2\" />"))
        assertTrue(xml.contains("<content>Hi, agent1!</content>"))
        assertTrue(xml.contains("</messages>"))
    }

    @Test
    fun `test thread operations end-to-end`() {
        // Register agents
        val agent1 = Agent(id = "agent1", name = "Agent 1")
        val agent2 = Agent(id = "agent2", name = "Agent 2")
        val agent3 = Agent(id = "agent3", name = "Agent 3")

        ThreadManager.registerAgent(agent1)
        ThreadManager.registerAgent(agent2)
        ThreadManager.registerAgent(agent3)

        // Create a thread
        val thread = ThreadManager.createThread(
            name = "Discussion Thread",
            creatorId = "agent1",
            participantIds = listOf("agent2")
        )

        assertNotNull(thread)
        assertEquals(2, thread?.participants?.size)

        // Add a participant
        val addSuccess = ThreadManager.addParticipant(
            threadId = thread?.id ?: "",
            participantId = "agent3"
        )

        assertTrue(addSuccess)
        assertEquals(3, ThreadManager.getThread(thread?.id ?: "")?.participants?.size)

        // Send messages
        val message1 = ThreadManager.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "agent1",
            content = "Welcome to the discussion!",
            mentions = listOf("agent2", "agent3")
        )

        assertNotNull(message1)
        assertEquals(2, message1?.mentions?.size)

        // Close the thread
        val closeSuccess = ThreadManager.closeThread(
            threadId = thread?.id ?: "",
            summary = "Productive discussion completed"
        )

        assertTrue(closeSuccess)
        val closedThread = ThreadManager.getThread(thread?.id ?: "")
        assertTrue(closedThread?.isClosed ?: false)
        assertEquals("Productive discussion completed", closedThread?.summary)
    }

    @Test
    fun `test list agents functionality`() {
        // Clear any existing agents (if possible)

        // Register multiple agents with different names and descriptions
        val agent1 = Agent(id = "test1", name = "Test Agent 1")
        val agent2 = Agent(id = "test2", name = "Test Agent 2", description = "Agent for testing")
        val agent3 = Agent(id = "test3", name = "Test Agent 3", description = "Another test agent")

        ThreadManager.registerAgent(agent1)
        ThreadManager.registerAgent(agent2)
        ThreadManager.registerAgent(agent3)

        // Get all agents
        val agents = ThreadManager.getAllAgents()

        // Verify all agents are returned
        assertTrue(agents.size >= 3) // There might be agents from other tests
        assertTrue(agents.contains(agent1))
        assertTrue(agents.contains(agent2))
        assertTrue(agents.contains(agent3))

        // Test with includeDetails = true
        val detailedOutput = agents.joinToString("\n") { agent -> 
            val description = if (agent.description.isNotEmpty()) {
                ", Description: ${agent.description}"
            } else {
                ""
            }
            "ID: ${agent.id}, Name: ${agent.name}${description}" 
        }
        assertTrue(detailedOutput.contains("ID: test1, Name: Test Agent 1"))
        assertTrue(detailedOutput.contains("ID: test2, Name: Test Agent 2, Description: Agent for testing"))
        assertTrue(detailedOutput.contains("ID: test3, Name: Test Agent 3, Description: Another test agent"))

        // Test with includeDetails = false
        val simpleOutput = agents.joinToString(", ") { agent -> agent.id }
        assertTrue(simpleOutput.contains("test1"))
        assertTrue(simpleOutput.contains("test2"))
        assertTrue(simpleOutput.contains("test3"))
    }
}
