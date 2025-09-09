package org.coralprotocol.coralserver.e2e

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.mockk.mockkConstructor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.agent.exceptions.AgentRequestException
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentServer
import org.coralprotocol.coralserver.agent.graph.GraphAgentServerSource
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryAgentExportPricing
import org.coralprotocol.coralserver.agent.registry.UnresolvedAgentExportSettings
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.Orchestrator
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.utils.ExternalSteppingKoog
import org.coralprotocol.coralserver.utils.ServerConnectionCoreDetails
import org.coralprotocol.coralserver.utils.ServerConnectionCoreDetailsImpl
import org.coralprotocol.coralserver.utils.createConnectedKoogAgent
import org.coralprotocol.coralserver.utils.renderDevmodeExporting
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi


class PaymentSessionScenarios {

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
            RegistryAgent(
                info = graphAgent.registryAgent.info,
                runtimes = LocalAgentRuntimes(functionRuntime = FunctionRuntime {}),
                options = mapOf(),
                unresolvedExportSettings = mapOf(
                    RuntimeId.FUNCTION to UnresolvedAgentExportSettings(
                        quantity = 1u,
                        pricing = RegistryAgentExportPricing(0L, 0L)
                    )
                )
            )
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testRequiresPayment(): Unit = runBlocking {
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
                registryAgent = RegistryAgent(
                    info = AgentRegistryIdentifier("test", "1.0.0").toInfo(),
                    runtimes = LocalAgentRuntimes(functionRuntime = FunctionRuntime {}),
                    options = mapOf(),
                    unresolvedExportSettings = mapOf()
                ),
                description = "",
                name = exportedServerAgentId,
                options = mapOf(),
                systemPrompt = "",
                customToolAccess = emptySet(),
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

            shouldThrowExactly<AgentRequestException.SessionNotFundedException> {
                getKoogAgent(exportingServer, exportedServerAgentId, exportingServer.server, claimId)
            }

            val koogAgentAfterPaid =
                getKoogAgent(exportingServer, exportedServerAgentId, exportingServer.server, claimId)
            importingServerAgent.step(um("Create a new thread with importingServerAgent and tell it the code 3251"))
            val retrievedCode = koogAgentAfterPaid.step(um("What is the code?"))
            assert(retrievedCode.content().contains("3251")) {
                "Retrieved code should contain 3251. Got: ${retrievedCode.content()}"
            }
        }
    }

    private suspend fun getKoogAgent(
        exportingServer: TestCoralServer,
        exportedServerAgentId: String,
        server: CoralServer?,
        claimId: String
    ): ExternalSteppingKoog = createConnectedKoogAgent(
        ServerConnectionCoreDetailsImpl(
            host = exportingServer.host,
            port = exportingServer.port,
            protocol = "http",
            namePassedToServer = exportedServerAgentId,
        ),
        renderServerUrl = {
            renderDevmodeExporting(
                server!!,
                claimId,
                exportedServerAgentId
            )
        }
    )
}
