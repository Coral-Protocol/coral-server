@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

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
    @SerialName("remote")
    @Description("Agent will be provided by another Coral server")
    data class Remote(
        @Description("The runtime that should be used for this remote agent.  Servers can export only specific runtimes so the runtime choice may narrow servers that can adequately provide the agent")
        val runtime: RuntimeId,

        @Description("A description of which servers should be queried for this remote agent request")
        @SerialName("server_source")
        val serverSource: GraphAgentServerSource,

        @Description("Customisation for the scoring of servers")
        @SerialName("server_scoring")
        val serverScoring: GraphAgentServerScoring? = GraphAgentServerScoring.Default()
    ) : GraphAgentProvider()
}