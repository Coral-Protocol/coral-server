@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph.server

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
@Suppress("unused")
sealed interface GraphAgentServerSource {
    @Serializable
    @SerialName("servers")
    data class Servers(
        val servers: List<GraphAgentServer>
    ) : GraphAgentServerSource


    @Serializable
    @SerialName("marketplace")
    object Marketplace : GraphAgentServerSource
}