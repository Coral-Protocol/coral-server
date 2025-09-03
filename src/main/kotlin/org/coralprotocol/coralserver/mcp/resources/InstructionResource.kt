package org.coralprotocol.coralserver.mcp.resources

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

const val INSTRUCTION_RESOURCE_URI = "Instructions.resource"

private fun CoralAgentIndividualMcp.handle(request: ReadResourceRequest): ReadResourceResult {
    return ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = "",
                uri = request.uri,
                mimeType = "text/markdown",
            )
        )
    )
}

fun CoralAgentIndividualMcp.addInstructionResource() {
    addResource(
        name = "instructions",
        description = "Coral instructions resource",
        uri = INSTRUCTION_RESOURCE_URI,
        mimeType = "text/markdown",
        readHandler = { request: ReadResourceRequest ->
            handle(request)
        },
    )
}
