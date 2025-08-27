package org.coralprotocol.coralserver.e2e

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

class RemoteAgentProxyingTest {

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testRemoteAgentProxying(): Unit = runBlocking {
        // Just to test proxying
        shouldNotThrowAny {
            val exportingServer = TestCoralServer(port = 14391u, devmode = true).apply { setup() }
            val importingServer = TestCoralServer(port = 14392u, devmode = true).apply { setup() }

            importingServer.sessionManager.getOrCreateSession("test", "aaa", "aaa", null)
            val agent = someKoogAgent()
            val response = agent.run("blahblahblah") // Step 1
            println("Agent response: $response")
            val response2 = agent.run("blahblahblah") // Step 1
            // Create an agent connecting to the exporting server
            // Create an agent connecting to the importing server
            // Get them to exchange a code
        }
    }

    private fun createAndConnectAgent() {

    }

    @Test
    fun testRemoteAgentOrchestration() {
        testApplication {
            val exportingServer = TestCoralServer(port = 14391u, devmode = true).apply { setup() }
            val importingServer = TestCoralServer(port = 14392u, devmode = true).apply { setup() }

            application {
                with(exportingServer.server!!) {
                    coralSeverModule()
                }
                // Get remote agent on importing server
                // Create session post to importing server
                //    with 2 agents, one local, one remote
                // mock the execution of the agents and confirm they get executed
            }
        }
    }
}
