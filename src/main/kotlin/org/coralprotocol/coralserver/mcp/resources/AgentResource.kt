package org.coralprotocol.coralserver.mcp.resources

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import org.coralprotocol.coralserver.mcp.McpResources.AGENT_RESOURCE_URI
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private fun CoralAgentIndividualMcp.handler(request: ReadResourceRequest): ReadResourceResult {
    val otherAgents = localSession
        .agents
        .filter { (name, _) -> name != connectedAgentId }
        .toList()
        .joinToString("\n") { (name, sessionAgent) -> "- $name: ${sessionAgent.description}" }

    // It's important that this list does not contain the requesting agent, they would otherwise try to communicate
    // with themselves.
    val agents = """
    # Coral resource: $AGENT_RESOURCE_URI
    This resource a list of agents and their descriptions
    
    ## Available agents
    $otherAgents
    
    """.trimIndent()

    return ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = agents,
                uri = request.uri,
                mimeType = "text/markdown",
            )
        )
    )
}

fun CoralAgentIndividualMcp.addAgentResource() {
    addResource(
        name = "message",
        description = "Message resource",
        uri = AGENT_RESOURCE_URI.toString(),
        mimeType = "text/markdown",
        readHandler = { request: ReadResourceRequest ->
            handler(request)
        },
    )
}
