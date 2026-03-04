@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph.plugin

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.mcp.McpToolManager
import org.coralprotocol.coralserver.session.SessionAgent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
@JsonClassDiscriminator("type")
sealed interface GraphAgentPlugin : KoinComponent {
    fun install(agent: SessionAgent)

    @Serializable
    @SerialName("close_session_tool")
    @Suppress("unused")
    object CloseSessionTool : GraphAgentPlugin {
        private val mcpToolManager by inject<McpToolManager>()

        override fun install(agent: SessionAgent) {
            agent.addMcpTool(mcpToolManager.closeSessionTool)
        }
    }
}