package org.coralprotocol.coralserver.mcpresources

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import nl.adaptivity.xmlutil.serialization.XML
import org.coralprotocol.coralserver.models.Thread
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private suspend fun CoralAgentIndividualMcp.handler(request: ReadResourceRequest): ReadResourceResult {
    val threadsAgentPrivyIn: List<Thread> = this.coralAgentGraphSession.getAllThreadsAgentParticipatesIn(this.connectedAgentId)
    val renderedThreads: String = XML.encodeToString(threadsAgentPrivyIn)
    return ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = "Message resource content",
                uri = request.uri,
                mimeType = "application/xml",
            )
        )
    )
}

fun CoralAgentIndividualMcp.addMessageResource() {
    addResource(
        name = "message",
        description = "Message resource",
        uri = "/message",
        mimeType = "application/json",
        readHandler = { request: ReadResourceRequest ->
            handler(request)
        },
    )
}
