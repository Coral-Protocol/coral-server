@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph.plugin

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
sealed interface GraphAgentPlugin {
    fun install()

    @Serializable
    @SerialName("close_session_tool")
    @Suppress("unused")
    object CloseSessionTool : GraphAgentPlugin {
        override fun install() {

        }
    }
}