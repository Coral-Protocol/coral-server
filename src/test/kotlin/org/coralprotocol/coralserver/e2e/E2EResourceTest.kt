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

    @OptIn(DelicateCoroutinesApi::class)
    val serverContext = newFixedThreadPoolContext(1, "E2EResourceTest")

    @OptIn(DelicateCoroutinesApi::class)
    @BeforeEach
    fun setup() {
        server.stop()
        server = CoralServer(port = port, sessionManager = SessionManager(), devmode = true)
        GlobalScope.launch(serverContext) {
            server.start()
        }
        patchMcpJavaContentType()
        patchMcpJavaEndpointResolution()
    }


    @Test
    fun testE2EResource() {
        val agent1 = createConnectedCoralAgent(server, "testAgent", "testAgentDescription", "testSystemPrompt")

        runBlocking {
            val response = agent1.ask("Create a thread and post a message in it")
            println("Agent name: ${agent1.name}")
            val sessions = server.sessionManager.getAllSessions()
            assert(sessions.size == 1) { "There should be one session" }
            println("Response: $response")
        }
    }
}


private fun patchMcpJavaContentType() {
    mockkStatic(HttpRequest::class)
    every { HttpRequest.newBuilder() } answers {
        println("MockK Interceptor [@BeforeEach]: HttpRequest.newBuilder() called. ")
        val requestBuilder = callOriginal().headers("Content-Type", "application/json")
        return@answers requestBuilder
    }
}

private fun patchMcpJavaEndpointResolution() {
    mockkStatic(io.modelcontextprotocol.util.Utils::class)
    every { resolveUri(any<URI>(), any<String>()) } answers {
        val baseUrl = invocation.args[0] as URI
        val endpointUrl = invocation.args[1] as String
        print("MockK Interceptor [@BeforeEach]: Utils.resolveUri called with baseUrl='$baseUrl', endpointUrl='$endpointUrl'. ")
        return@answers if (endpointUrl.contains("?sessionId")) {
            // In this case the sessionId is an MCP sessionId, not a Coral sessionId.
            // The resolution logic works in this case (though the original is resolving against a URI object)
            baseUrl.resolve(endpointUrl)
        } else {
            baseUrl
        }
    }
}


