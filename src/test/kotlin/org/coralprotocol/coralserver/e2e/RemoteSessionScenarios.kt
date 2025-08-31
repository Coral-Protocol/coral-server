package org.coralprotocol.coralserver.e2e

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.ktor.http.Url
import io.ktor.server.testing.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.server.CoralServer
import org.coralprotocol.coralserver.server.ExportManager
import org.coralprotocol.coralserver.utils.ServerConnectionCoreDetails
import org.coralprotocol.coralserver.utils.UserMessage
import org.coralprotocol.coralserver.utils.createConnectedKoogAgent
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi

typealias um = UserMessage
class RemoteSessionScenarios {

    /**
     * Sets up
     */
    fun CoralServer.proxyAgent(agentId: String): Url = Url("http://${host}:${port}/sse/v1/export/{agentId}")
    fun ServerConnectionCoreDetails.renderDevmodeExporting()= "$protocol://$host:$port/sse/v1/export/$namePassedToServer&agentDescription=$descriptionPassedToServer"

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun testRemoteAgentProxying(): Unit = runBlocking {
        // Just to test proxying
        shouldNotThrowAny {
            val exportingServer = TestCoralServer(port = 14391u, devmode = true).apply { setup() }
            val importingServer = TestCoralServer(port = 14392u, devmode = true).apply { setup() }

            val session = importingServer.sessionManager.getOrCreateSession("test", "aaa", "aaa", null)
            launch { session.waitForAgentCount(1, 2000) }
            // TODO: Create DSL for building groups and waiting for them properly
            val importServerAgentId = "importingServerAgent"
            val importingServerAgent =
                createConnectedKoogAgent(importingServer.server!!, importServerAgentId, session = session)
            val exportingServerAgentUrl = exportingServer.server!!.proxyAgent(importServerAgentId)

//            val exportingServerAgent =
//                createConnectedKoogAgent(
//                    host = exportingServerAgentUrl.host,
//                    port = exportingServerAgentUrl.port.toUShort(),
//                    protocol = exportingServerAgentUrl.protocol.name,
//                    namePassedToServer = "exportingServerAgent",
//                    session = session,
//                )
            val exportingServerAgent =
                createConnectedKoogAgent(
                    ServerConnectionCoreDetails(
                        host = exportingServerAgentUrl.host,
                        port = exportingServerAgentUrl.port.toUShort(),
                        protocol = exportingServerAgentUrl.protocol.name,
                        namePassedToServer = "exportingServerAgent",
                        sessionId = session.id,
                        applicationId = TODO(),
                        privacyKey = TODO(),
                        descriptionPassedToServer = TODO()
                    )
                )


            importingServerAgent.step(um("Create a new thread with importingServerAgent and tell it the code 3251"))
            //TODO: WIP
            // Create an agent connecting to the exporting server
            // Create an agent connecting to the importing server
            // Get them to exchange a code
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
