package org.coralprotocol.coralserver.agent.graph

import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.Serializable

@Serializable
data class GraphAgentTool(
    val transport: GraphAgentToolTransport,
    val inputSchema: ToolSchema = ToolSchema(),
    val outputSchema: ToolSchema = ToolSchema(),
    val description: String? = null,
    val title: String? = null,
    val annotations: ToolAnnotations? = null,
)