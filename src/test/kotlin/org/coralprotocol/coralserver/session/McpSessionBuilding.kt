package org.coralprotocol.coralserver.session

import io.ktor.client.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.UniqueAgentName
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.AddressConsumer

open class McpSessionBuilding : SessionBuilding() {
    fun HttpClient.mcpFunctionRuntime(name: String, func: suspend (Client, LocalSession) -> Unit) =
        FunctionRuntime { executionContext, applicationRuntimeContext ->
            val mcpClient = Client(
                clientInfo = Implementation(
                    name = name,
                    version = "1.0.0"
                )
            )

            val transport = SseClientTransport(
                client = this,
                urlString = applicationRuntimeContext.getMcpUrl(
                    executionContext,
                    AddressConsumer.LOCAL
                ).toString()
            )
            mcpClient.connect(transport)
            func(mcpClient, executionContext.session)
        }

    fun buildSession(agents: Map<UniqueAgentName, suspend (Client, LocalSession) -> Unit>) = sseEnv {
        val (session, _) = sessionManager.createSession(
            "test", AgentGraph(
                agents = agents.mapValues { (name, func) ->
                    graphAgent(
                        registryAgent = registryAgent(
                            name = name,
                            functionRuntime = client.mcpFunctionRuntime(name, func)
                        ),
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    ).second
                },
                customTools = mapOf(),
                groups = setOf(agents.keys.toSet())
            )
        )

        session.launchAgents()
        session.joinAgents()
    }
}