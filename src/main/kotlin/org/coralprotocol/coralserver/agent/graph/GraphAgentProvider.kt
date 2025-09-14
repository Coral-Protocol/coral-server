@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.exceptions.AgentRequestException
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider.RemoteRequest
import org.coralprotocol.coralserver.agent.graph.server.GraphAgentServer
import org.coralprotocol.coralserver.agent.graph.server.GraphAgentServerScoring
import org.coralprotocol.coralserver.agent.graph.server.GraphAgentServerSource
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.registry.AgentRegistryIdentifier
import org.coralprotocol.coralserver.agent.registry.PublicAgentExportSettings
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.payment.JupiterService

private val logger = KotlinLogging.logger {}

@Serializable
@JsonClassDiscriminator("type")
@Description("A local or remote provider for an agent")
sealed class GraphAgentProvider {
    @Serializable
    @SerialName("local")
    @Description("The agent will be provided by this server")
    data class Local(
        val runtime: RuntimeId,
    ) : GraphAgentProvider()

    @Serializable
    @SerialName("remote_request")
    @Description("A request for a remote agent and a list of places to try and source a server from")
    data class RemoteRequest(
        @Description("The runtime that should be used for this remote agent.  Servers can export only specific runtimes so the runtime choice may narrow servers that can adequately provide the agent")
        val runtime: RuntimeId,

        @Description("The maximum we are willing to pay for this remote agent, note that if this is not high enough there may be no remotes willing to provide the agent")
        val maxCost: AgentClaimAmount,

        @Description("A description of which servers should be queried for this remote agent request")
        val serverSource: GraphAgentServerSource,

        @Description("Customisation for the scoring of servers")
        val serverScoring: GraphAgentServerScoring? = GraphAgentServerScoring.Default()
    ) : GraphAgentProvider()

    @Serializable
    @SerialName("remote")
    @Description("A remote agent provided by a specific server")
    data class Remote(
        @Description("The server that is providing this remote agent")
        val server: GraphAgentServer,

        @Description("The runtime to be used on the remote server.  Likely Docker or Phala")
        val runtime: RuntimeId,

        @Description("The wallet address of the server that is providing this remote agent")
        val wallet: String,

        @Description("The max cost of this agent")
        val maxCost: AgentClaimAmount,

        @Description("The payment session ID for this remote agent.  This will be shared with all other remote agents in the graph")
        val paymentSessionId: String,
    ) : GraphAgentProvider()
}


suspend fun RemoteRequest.toRemote(
    agentId: AgentRegistryIdentifier,
    paymentSessionId: String,
    jupiterService: JupiterService
): GraphAgentProvider.Remote {
    val rankedServers = when (serverSource) {
        is GraphAgentServerSource.Servers -> {
            serverSource.servers.sortedBy {
                serverScoring?.getScore(it) ?: 1.0
            }
        }

        is GraphAgentServerSource.Indexer -> throw AgentRequestException.NoServer("Server indexers are not supported yet")
    }

    var selectedServer: GraphAgentServer? = null
    var exportSettings: PublicAgentExportSettings? = null

    for (server in rankedServers) {
        try {
            exportSettings = server.getAgentExportSettings(agentId)[runtime]

            // A server must provide the required runtime, and it most not have a max cost outside the exported agent's
            // comfortable max cost range
            if (exportSettings != null && exportSettings.pricing.withinRange(maxCost, jupiterService)) {
                selectedServer = server
                break
            }
        }
        catch (e: Exception) {
            logger.warn(e) { "Exception throw when trying to get export settings for agent $agentId on server $server" }
        }
    }

    if (selectedServer == null)
        throw AgentRequestException.NoServer("No servers available for this remote agent")

    return GraphAgentProvider.Remote(
        server = selectedServer,
        runtime = runtime,
        wallet = selectedServer.getWallet(),
        maxCost = maxCost,
        paymentSessionId = paymentSessionId,
    )
}