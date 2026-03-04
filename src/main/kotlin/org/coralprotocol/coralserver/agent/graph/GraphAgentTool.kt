package org.coralprotocol.coralserver.agent.graph

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.Serializable

@Serializable
data class GraphAgentTool(
    val transport: GraphAgentToolTransport,
    val schema: ToolSchema,
)