package org.coralprotocol.coralserver.e2e

import io.mockk.every
import io.mockk.mockkStatic
import io.modelcontextprotocol.util.Utils.resolveUri
import kotlinx.coroutines.*
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.SessionManager
import org.eclipse.lmos.arc.agents.agent.ask
import org.junit.jupiter.api.BeforeEach
import java.net.URI
import java.net.http.HttpRequest
import kotlin.test.Test


class E2EResourceTest {
    val port = 14391
    var server = CoralServer(port = port, devmode = true)
    val context = newFixedThreadPoolContext(1, "E2EResourceTest")

    @OptIn(DelicateCoroutinesApi::class)
    @BeforeEach
    fun setup() {
        server.stop()
        server = CoralServer(port = port, sessionManager = SessionManager(), devmode = true)
        GlobalScope.launch(context) {
            server.start()
        }
        mockkStatic(io.modelcontextprotocol.util.Utils::class)
        mockkStatic(HttpRequest::class)
        every { HttpRequest.newBuilder() } answers {
            println("MockK Interceptor [@BeforeEach]: HttpRequest.newBuilder() called. ")
            val requestBuilder = callOriginal().headers("Content-Type", "application/json")
            return@answers requestBuilder
        }


        every { resolveUri(any<URI>(), any<String>()) } answers {
            val actualBaseUrl = invocation.args[0] as URI
            val actualEndpointUrl = invocation.args[1] as String
            print("MockK Interceptor [@BeforeEach]: Utils.resolveUri called with baseUrl='$actualBaseUrl', endpointUrl='$actualEndpointUrl'. ")

            println("Using original endpoint '$actualBaseUrl'")

            return@answers if (actualEndpointUrl.contains("?sessionId")) {
                actualBaseUrl.resolve(actualEndpointUrl)
            } else {
                actualBaseUrl
            }
        }
    }


    @Test
    fun testE2EResource() {
        // Create a connected Coral agent using the utility
        val agent1 = createConnectedCoralAgent(server, "testAgent", "testAgentDescription", "testSystemPrompt")

        // Test the agent
        runBlocking {
//            delay(100000)
//            agentgetAgents().forEach { agent ->
//                val chatAgent = agent as ChatAgent
//                println("Agent name: ${chatAgent.name}")
//                val resp = chatAgent.ask("Create a thread and post a message in it")
//                val sessions = SessionManager.getAllSessions()
//                assertEquals(1, sessions.size, "There should be one session")
//                println("Response: $resp")
//            }
            val response = agent1.ask("Create a thread and post a message in it")
            println("Agent name: ${agent1.name}")
            val sessions = server.sessionManager.getAllSessions()
            assert(sessions.size == 1) { "There should be one session" }
            println("Response: $response")
        }
    }
}



