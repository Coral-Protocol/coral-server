@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.koin.core.component.KoinComponent

@Serializable
@JsonClassDiscriminator("type")
@Description("A local or remote provider for an agent")
sealed class GraphAgentProvider : KoinComponent {
    abstract val runtime: RuntimeId

    @Serializable
    @SerialName("local")
    @Description("The agent will be provided by this server")
    data class Local(
        override val runtime: RuntimeId,
    ) : GraphAgentProvider()

    @Serializable
    @SerialName("linked")
    @Description("The agent will be provided by a linked server")
    data class Linked(
        val linkedServerName: String,
        override val runtime: RuntimeId,
    ) : GraphAgentProvider()
}