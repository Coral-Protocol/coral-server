package org.coralprotocol.coralserver.routes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.session.SessionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SseRoutesTest {

    @BeforeEach
    fun setup() {
        // Clear any existing sessions
        SessionManager.getAllSessions().forEach { it.clearAll() }
    }

    @AfterEach
    fun tearDown() {
        // Clear any existing sessions
        SessionManager.getAllSessions().forEach { it.clearAll() }
    }

    @Test
    fun `test simultaneous connections with waitForAgents`() = runBlocking {
        // Create a session
        val session = SessionManager.getOrCreateSession("test-session", "test-app", "test-key")
        session.devRequiredAgentStartCount = 3

        // Create a counter to track the reported agent counts
        val reportedCounts = ConcurrentHashMap<Int, AtomicInteger>()

        // Create a mutex to simulate the synchronization that would happen in a real server
        val mutex = kotlinx.coroutines.sync.Mutex()

        // Launch 3 coroutines to simulate 3 simultaneous connections
        val connectionJobs = List(3) { index ->
            launch(Dispatchers.IO) {
                // Simulate a very small delay between connections to ensure they're not exactly simultaneous
                // but close enough to reproduce the issue
//                delay(10L * index)

                // Create the agent
                val agent = Agent(
                    id = "agent-${index + 1}",
                    name = "Agent ${index + 1}",
                    description = "Test agent ${index + 1}"
                )

                // Simulate the handleSseConnection function
//                mutex.lock()
                try {
                    // Get the current agent count before registering
                    val currentCount = session.getRegisteredAgentsCount()
                    println("[DEBUG_LOG] Agent ${index + 1} sees current count: $currentCount before registering")
                    System.out.println("[DEBUG_LOG] Agent ${index + 1} sees current count: $currentCount before registering")

                    // Record the count that was observed
                    reportedCounts.computeIfAbsent(currentCount) { AtomicInteger(0) }.incrementAndGet()

                    // Register the agent
                    session.registerAgent(agent)

                    // Get the new count after registering
                    val newCount = session.getRegisteredAgentsCount()
                    println("[DEBUG_LOG] Agent ${index + 1}: New count after registering: $newCount")
                    System.out.println("[DEBUG_LOG] Agent ${index + 1}: New count after registering: $newCount")
                } finally {
//                    mutex.unlock()
                }

                // If this is one of the first two agents, wait for all agents to be registered
                if (index < 2) {
                    val success = session.waitForAgentCount(3, 5000)
                    println("[DEBUG_LOG] Agent ${index + 1} wait result: $success")
                    assertTrue(success, "Wait for agent count should succeed")
                }
            }
        }

        // Wait for all connections to complete
        connectionJobs.forEach { it.join() }

        // Print the reported counts for debugging
        println("[DEBUG_LOG] Reported counts: ${reportedCounts.entries.joinToString { "${it.key}=${it.value.get()}" }}")

        // Verify that we got different counts reported
        // If the issue is fixed, we should see counts 0, 1, and 2 reported
        // If the issue is not fixed, we would see count 0 reported 3 times
        assertEquals(1, reportedCounts[0]?.get() ?: 0, "Count 0 should be reported once")
        assertEquals(1, reportedCounts[1]?.get() ?: 0, "Count 1 should be reported once")
        assertEquals(1, reportedCounts[2]?.get() ?: 0, "Count 2 should be reported once")

        // Verify that all 3 agents are registered
        assertEquals(3, session.getRegisteredAgentsCount(), "All 3 agents should be registered")
    }
}
