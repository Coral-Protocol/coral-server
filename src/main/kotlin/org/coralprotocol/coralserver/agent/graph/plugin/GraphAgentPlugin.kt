@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.graph.plugin

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.mcp.addMcpTool
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.mcp.tools.optional.CloseSessionTool as CloseSessionMcpTool

@Serializable
@JsonClassDiscriminator("type")
sealed interface GraphAgentPlugin {
    fun install(mcpServer: CoralAgentIndividualMcp)

    @Serializable
    @SerialName("close_session_tool")
    @Suppress("unused")
    object CloseSessionTool : GraphAgentPlugin {
        override fun install(mcpServer: CoralAgentIndividualMcp) {
            mcpServer.addMcpTool(CloseSessionMcpTool())
        }
    }
}