package org.coralprotocol.coralserver.session

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.AddressConsumer
import kotlin.test.Test
import kotlin.test.assertNotNull

class FunctionRuntimeTest : SessionBuilding() {
    @Test
    fun testSse() = sseEnv {
        withContext(Dispatchers.IO) {
            val functionRuntime = FunctionRuntime { executionContext, applicationRuntimeContext ->
                val mcpClient = Client(
                    clientInfo = Implementation(
                        name = "test",
                        version = "1.0.0"
                    )
                )

                val transport = SseClientTransport(
                    client = client,
                    urlString = applicationRuntimeContext.getMcpUrl(executionContext, AddressConsumer.LOCAL).toString()
                )
                mcpClient.connect(transport)

                val toolResult = mcpClient.listTools()
                assertNotNull(toolResult)
                assert(toolResult.tools.isNotEmpty())
            }

            val (session, _) = sessionManager.createSession("test", AgentGraph(
                agents = mapOf(
                    "agent1" to graphAgent(
                        registryAgent = registryAgent(
                            functionRuntime = functionRuntime
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    ),
                ),
                customTools = mapOf(),
                groups = setOf()
            ))

            session.launchAgents()
            session.waitForAgents()
        }
    }
}