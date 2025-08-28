package org.coralprotocol.coralserver.e2e

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.ktor.server.testing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.UserMessage
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

class RemoteSessionScenarios {

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testRemoteAgentProxying(): Unit = runBlocking {
        // Just to test proxying
        shouldNotThrowAny {
            val exportingServer = TestCoralServer(port = 14391u, devmode = true).apply { setup() }
            val importingServer = TestCoralServer(port = 14392u, devmode = true).apply { setup() }

            val session = importingServer.sessionManager.getOrCreateSession("test", "aaa", "aaa", null)
            // TODO: Create DSL for building groups and waiting for them properly
            val agent = createConnectedKoogAgent(importingServer.server!!, "testAgent", session = session)
            // TODO: Create DSL for building groups and waiting for them properly
            launch { session.waitForAgentCount(1, 2000) }
            val response = agent.step(UserMessage("Please create a thread called test and say hello in it"))

            assert(session.getAllThreads().any { it.name == "test" }) { "Thread 'test' should be created" }
            //TODO: WIP
            // Create an agent connecting to the exporting server
            // Create an agent connecting to the importing server
            // Get them to exchange a code
        }
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
