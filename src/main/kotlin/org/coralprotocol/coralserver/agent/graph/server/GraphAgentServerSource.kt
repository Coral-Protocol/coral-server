@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed class GraphAgentServerSource {
    @Serializable
    @SerialName("servers")
    data class Servers(
        val servers: List<GraphAgentServer>
    ) : GraphAgentServerSource()

    // TODO: implement this properly!
    // an indexer will be a server that will provide another list of servers to query.  We will allow people to host
    // their own indexers and we will also provide an indexer connected to our agent marketplace.
    @Serializable
    data class Indexer(
        val indexer: String
    ) : GraphAgentServerSource()
}