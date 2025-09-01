package org.coralprotocol.coralserver.e2e

import io.kotest.assertions.throwables.shouldNotThrowAny
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.agent.graph.GraphAgent
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.graph.GraphAgentServer
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.session.CoralAgentGraphSession
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

    fun ServerConnectionCoreDetails.renderDevmodeExporting(server: CoralServer, externalId: String, agentId: String): String {

        return "$protocol://$host:$port/sse/v1/export/$externalId?agentId=$agentId&agentDescription=$descriptionPassedToServer"
    }

    /**
     * Used in local cases
     */
    fun ServerConnectionCoreDetails.renderWithSessionDetails(session: CoralAgentGraphSession) =
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

//    fun test() {
//        val websocketClient = HttpClient(CIO) {
//            install(ContentNegotiation) {
//                json()
//            }
//            install(WebSockets)
//        }
//
//        runBlocking {
//            websocketClient.webSocket(
//                host = server.address,
//                port = server.port.toInt(),
//                path = "/ws/v1/exported/$remoteSessionId",
//            ) {
//                // something!
//            }
//        }
//    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testRemoteAgentProxying(): Unit = runBlocking {
        // Just to test proxying
        shouldNotThrowAny {
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
            val claimId = exportingServer.server!!.remoteSessionManager.createClaim(
                GraphAgent(
                    name = exportedServerAgentId,
                    options = mapOf(),
                    systemPrompt = "",
                    extraTools = emptySet(),
                    blocking = false,
                    provider = GraphAgentProvider.Local(RuntimeId.DOCKER)
                )
            )

            // SHOULD HAVE STARTED THE AGENT
            val gas = GraphAgentServer(exportingServer.host, exportingServer.port, emptyList())
            launch {
                importingServer.server!!.sessionManager.orchestrator.establishConnection(gas, claimId)
            }

            delay(1000)

            val exportingServerAgent =
                createConnectedKoogAgent(
                    ServerConnectionCoreDetailsImpl(
                        host = exportingServer.host,
                        port = exportingServer.port,
                        protocol = "http",
                        namePassedToServer = exportedServerAgentId,
                    ), renderServerUrl = { renderDevmodeExporting(exportingServer.server!!, claimId, exportedServerAgentId) }
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
