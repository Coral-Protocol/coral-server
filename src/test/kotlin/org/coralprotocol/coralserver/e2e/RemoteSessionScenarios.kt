package org.coralprotocol.coralserver.e2e

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.mockk.mockkConstructor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.server.GraphAgentServer
import org.coralprotocol.coralserver.agent.graph.server.GraphAgentServerSource
import org.coralprotocol.coralserver.agent.graph.toRemote
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryAgentExportPricing
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettings
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
//import org.coralprotocol.coralserver.payment.orchestration.SessionNotFundedException
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.utils.ServerConnectionCoreDetails
import org.coralprotocol.coralserver.utils.ServerConnectionCoreDetailsImpl
import org.coralprotocol.coralserver.utils.UserMessage
import org.coralprotocol.coralserver.utils.createConnectedKoogAgent
import org.coralprotocol.coralserver.utils.renderDevmodeExporting
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

typealias um = UserMessage

class RemoteSessionScenarios {

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
        (registry.agents as MutableList).add(
            RegistryAgent(
                info = graphAgent.registryAgent.info,
                runtimes = LocalAgentRuntimes(functionRuntime = FunctionRuntime {}),
                options = mapOf(),
                unresolvedExportSettings = mapOf()
            )
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
        (registry.agents as MutableList).add(
            graphAgent.registryAgent
//            RegistryAgent(
//                info = graphAgent.registryAgent.info,
//                runtimes = LocalAgentRuntimes(functionRuntime = FunctionRuntime {}),
//                options = mapOf(),
//                unresolvedExportSettings = mapOf(
//                    RuntimeId.FUNCTION to UnresolvedAgentExportSettings(
//                        quantity = 1u,
//                        pricing = RegistryAgentExportPricing(0, 0L)
//                    )
//                )
//            )
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
            val exportedServerAgentId = "exportingServerAgent"
            val importServerAgentName = "importingServerAgent"

            val agentRegistryIdentifier = AgentRegistryIdentifier("test", "1.0.0")
            val exportingSideExportedGraphAgent = GraphAgent(
                registryAgent = RegistryAgent(
                    info = agentRegistryIdentifier.toInfo(),
                    runtimes = LocalAgentRuntimes(functionRuntime = FunctionRuntime {}),
                    options = mapOf(),
                    unresolvedExportSettings = mapOf(
                        RuntimeId.FUNCTION to UnresolvedAgentExportSettings(
                            quantity = 1u,
                            pricing = RegistryAgentExportPricing(0L, 1000000)
                        )
                    )
                ),
                description = "",
                name = exportedServerAgentId,
                options = mapOf(),
                systemPrompt = "",
                customToolAccess = emptySet(),
                blocking = false,
                plugins = emptySet(),
                provider = GraphAgentProvider.Local(
                    runtime = RuntimeId.FUNCTION,
                )
            )

            // SHOULD HAVE STARTED THE AGENT
            exportingServer.server!!.localSessionManager.orchestrator.exportAnonymousAgent(
                exportingSideExportedGraphAgent
            )
            delay(4000)

            val importingSideExportedGraphAgent = exportingSideExportedGraphAgent.copy(
                provider = GraphAgentProvider.RemoteRequest(
                    runtime = RuntimeId.FUNCTION,
                    serverSource = GraphAgentServerSource.Servers(
                        listOf(
                            GraphAgentServer(
                                exportingServer.host,
                                exportingServer.port,
                                secure = false,
                                emptyList()
                            )
                        )
                    ),
                    serverScoring = null,
                    maxCost = 1000L
                )
            )


            val ps = importingServer.server!!.localSessionManager.createPaymentSession(
                agentGraph = AgentGraph(
                    agents = mapOf(exportedServerAgentId to importingSideExportedGraphAgent),
                    emptyMap(),
                    setOf(setOf(exportedServerAgentId, importServerAgentName))
                )
            )

            val importingServerSession =
                importingServer.getSessionManager().getOrCreateSession("test", "aaa", "aaa", null, incomingSessionInfo = ps!!)
            launch { importingServerSession.waitForAgentCount(2, 2000) }
            // TODO: Create DSL for building groups and waiting for them properly
            val importingServerAgent =
                createConnectedKoogAgent(
                    getServerConnectionDetails(importingServer, importServerAgentName),
                    renderServerUrl = { renderWithSessionDetails(importingServerSession) })

            importingServer.server!!.localSessionManager.orchestrator.spawnAnonymousAgent(
                importingServerSession,
                importingSideExportedGraphAgent.copy(provider = (importingSideExportedGraphAgent.provider as GraphAgentProvider.Remote)),
                importServerAgentName,
                importingServerSession.applicationId,
                importingServerSession.privacyKey
            )

            delay(2000)
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

}
