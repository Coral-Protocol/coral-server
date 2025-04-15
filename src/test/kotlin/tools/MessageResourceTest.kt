package org.coralprotocol.agentfuzzyp2ptools.tools

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.coralprotocol.agentfuzzyp2ptools.resources.MessageResource

class MessageResourceTest {
    private lateinit var messageResource: MessageResource

    @BeforeEach
    fun setup() {
        messageResource = MessageResource()
    }

    @Test
    fun `test adding and retrieving messages for single agent`() {
        val agentId = "agent1"
        val message1 = "Hello"
        val message2 = "World"

        messageResource.addMessage(agentId, message1)
        messageResource.addMessage(agentId, message2)

        val messages = messageResource.getMessagesForAgent(agentId)
        assertEquals(2, messages.size)
        assertEquals(message1, messages[0])
        assertEquals(message2, messages[1])
    }

    @Test
    fun `test retrieving messages for non-existent agent returns empty list`() {
        val messages = messageResource.getMessagesForAgent("nonexistent")
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `test messages for different agents are stored separately`() {
        val agent1 = "agent1"
        val agent2 = "agent2"
        
        messageResource.addMessage(agent1, "Message for agent 1")
        messageResource.addMessage(agent2, "Message for agent 2")

        val messages1 = messageResource.getMessagesForAgent(agent1)
        val messages2 = messageResource.getMessagesForAgent(agent2)

        assertEquals(1, messages1.size)
        assertEquals(1, messages2.size)
        assertEquals("Message for agent 1", messages1[0])
        assertEquals("Message for agent 2", messages2[0])
    }

    @Test
    fun `test state flow updates when messages are added`() = runBlocking {
        val agentId = "agent1"
        val message = "Test message"

        messageResource.addMessage(agentId, message)
        
        val stateFlowValue = messageResource.getMessageUpdatesFlow().first()
        assertTrue(stateFlowValue.containsKey(agentId))
        assertEquals(message, stateFlowValue[agentId]?.first())
    }

    @Test
    fun `test messages are added in correct order`() {
        val agentId = "agent1"
        val messages = listOf("First", "Second", "Third")

        messages.forEach { messageResource.addMessage(agentId, it) }

        val retrievedMessages = messageResource.getMessagesForAgent(agentId)
        assertEquals(messages.size, retrievedMessages.size)
        messages.forEachIndexed { index, message ->
            assertEquals(message, retrievedMessages[index])
        }
    }
} 