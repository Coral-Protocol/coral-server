package org.coralprotocol.coralserver.e2e

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.mockk.mockkConstructor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentServer
import org.coralprotocol.coralserver.agent.graph.GraphAgentServerSource
import org.coralprotocol.coralserver.agent.registry.AgentExport
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.utils.ServerConnectionCoreDetails
import org.coralprotocol.coralserver.utils.ServerConnectionCoreDetailsImpl
import org.coralprotocol.coralserver.utils.UserMessage
import org.coralprotocol.coralserver.utils.createConnectedKoogAgent
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

typealias um = UserMessage

class RemoteSessionScenarios {

    /**
     * (Does )
     */

    fun ServerConnectionCoreDetails.renderDevmodeExporting(
        server: CoralServer,
        externalId: String,
        agentId: String
    ): String {

        return "$protocol://$host:$port/sse/v1/export/$externalId?agentId=$agentId&agentDescription=$descriptionPassedToServer"
    }

    /**
     * Used in local cases
     */
    fun ServerConnectionCoreDetails.renderWithSessionDetails(session: LocalSession) =
        "$protocol://$host:$port/sse/v1/devmode/${session.applicationId}/${session.privacyKey}/${session.id}/sse?agentId=${namePassedToServer}&agentDescription=$descriptionPassedToServer"


    fun getServerConnectionDetails(
        server: TestCoralServer,
        namePassedToSerer: String,
        descriptionPassedToServer: String = namePassedToSerer
    ): ServerConnectionCoreDetails {
        return ServerConnectionCoreDetailsImpl(
            host = server.host,
            port = server.port,
            protocol = "http",
            namePassedToServer = namePassedToSerer,
            descriptionPassedToServer = descriptionPassedToServer
        )
    }


    fun Orchestrator.spawnAnonymousAgent(
        session: LocalSession,
        graphAgent: GraphAgent,
        agentName: String,
        applicationId: String,
        privacyKey: String
    ) {
        (registry.importedAgents as MutableMap).putIfAbsent(
            graphAgent.name,
            RegistryAgent(LocalAgentRuntimes(functionRuntime = FunctionRuntime({})), emptyMap())
        )
        spawn(
            session,
            graphAgent,
            agentName,
            applicationId,
            privacyKey
        )
    }

    fun Orchestrator.exportAnonymousAgent(
        graphAgent: GraphAgent,
    ) {
        (registry.importedAgents as MutableMap).putIfAbsent(
            graphAgent.name,
            RegistryAgent(LocalAgentRuntimes(functionRuntime = FunctionRuntime({})), emptyMap())
        )
        (registry.exportedAgents as MutableMap).putIfAbsent(
            graphAgent.name,
            AgentExport(RegistryAgent(LocalAgentRuntimes(functionRuntime = FunctionRuntime({})), emptyMap()), emptyMap(), quantity = 10u)
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testRemoteAgentProxying(): Unit = runBlocking {
        // Just to test proxying
        shouldNotThrowAny {
            mockkConstructor(Orchestrator::class)
            val exportingServer = TestCoralServer(port = 4001u, devmode = true).apply { setup() }
            val importingServer = TestCoralServer(port = 4002u, devmode = true).apply { setup() }

            val importingServerSession =
                importingServer.getSessionManager().getOrCreateSession("test", "aaa", "aaa", null)
            launch { importingServerSession.waitForAgentCount(2, 2000) }
            // TODO: Create DSL for building groups and waiting for them properly
            val importServerAgentName = "importingServerAgent"
            val importingServerAgent =
                createConnectedKoogAgent(
                    getServerConnectionDetails(importingServer, importServerAgentName),
                    renderServerUrl = { renderWithSessionDetails(importingServerSession) })

            val exportedServerAgentId = "exportingServerAgent"
            val graphAgent = GraphAgent(
                name = exportedServerAgentId,
                options = mapOf(),
                systemPrompt = "",
                extraTools = emptySet(),
                blocking = false,
                provider = GraphAgentProvider.Remote(
                    runtime = RuntimeId.FUNCTION,
                    serverSource = GraphAgentServerSource.Servers(
                        listOf(
                            GraphAgentServer(
                                exportingServer.host,
                                exportingServer.port,
                                emptyList()
                            )
                        )
                    ),
                    serverScoring = null,
                )
            )


            // SHOULD HAVE STARTED THE AGENT
            exportingServer.server!!.localSessionManager.orchestrator.exportAnonymousAgent(
                graphAgent
            )
            importingServer.server!!.localSessionManager.orchestrator.spawnAnonymousAgent(
                importingServerSession,
                graphAgent,
                importServerAgentName,
                importingServerSession.applicationId,
                importingServerSession.privacyKey
            )


            delay(1000)
            println("$importingServer")
            val claimId = exportingServer.server!!.remoteSessionManager.claims.keys.first()


            val exportingServerAgent =
                createConnectedKoogAgent(
                    ServerConnectionCoreDetailsImpl(
                        host = exportingServer.host,
                        port = exportingServer.port,
                        protocol = "http",
                        namePassedToServer = exportedServerAgentId,
                    ),
                    renderServerUrl = {
                        renderDevmodeExporting(
                            exportingServer.server!!,
                            claimId,
                            exportedServerAgentId
                        )
                    }
                )

            importingServerAgent.step(um("Create a new thread with importingServerAgent and tell it the code 3251"))
            val retrievedCode = exportingServerAgent.step(um("What is the code?"))
            assert(retrievedCode.content().contains("3251")) {
                "Retrieved code should contain 3251. Got: ${retrievedCode.content()}"
            }
        }
    }

    @Test
    fun testRemoteAgentOrchestration() = runBlocking {
        TODO("Finish this test")
        shouldNotThrowAny {
            val exportingServer = TestCoralServer(port = 14391u, devmode = true).apply { setup() }
            val importingServer = TestCoralServer(port = 14392u, devmode = true).apply { setup() }


            // Get remote agent on importing server
            // Create session post to importing server
            //    with 2 agents, one local, one remote
            // mock the execution of the agents and confirm they get executed
        }
    }
}
