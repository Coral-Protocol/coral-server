package org.coralprotocol.coralserver.session.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.agent.graph.plugin.GraphAgentPlugin
import org.coralprotocol.coralserver.session.CustomTool
import org.coralprotocol.coralserver.x402.X402BudgetedResource
import java.util.UUID


@Serializable
data class SessionAgent(
    val id: String,
    var description: String = "",
    var state: SessionAgentState = SessionAgentState.Disconnected,
    var mcpUrl: String?,
    val extraTools: Set<CustomTool> = setOf(),
    val coralPlugins: Set<GraphAgentPlugin> = setOf(),

    @Transient
    val secret: String = UUID.randomUUID().toString(),

    @Transient
    val x402BudgetedResources: List<X402BudgetedResource> = listOf()
)