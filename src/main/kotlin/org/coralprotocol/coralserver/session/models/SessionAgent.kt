package org.coralprotocol.coralserver.session.models

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.session.CustomTool


@Serializable
data class SessionAgent(
    val id: String,
    var description: String = "",
    var state: SessionAgentState = SessionAgentState.Disconnected,
    var mcpUrl: String?,
    val extraTools: Set<CustomTool> = setOf(),
    val coralPlugins: Set<GraphAgentPlugin> = setOf()
)