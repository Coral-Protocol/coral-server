package org.coralprotocol.coralserver.mcpresources

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.serialization.XML
import org.coralprotocol.coralserver.models.ResolvedThread
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp

private fun CoralAgentIndividualMcp.handler(request: ReadResourceRequest): ReadResourceResult {
    val threadsAgentPrivyIn: List<ResolvedThread> = this.coralAgentGraphSession.getAllThreadsAgentParticipatesIn(this.connectedAgentId).map { it -> it.resolve() }
    val renderedThreads: String = threadsAgentPrivyIn.render()
    return ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = renderedThreads,
                uri = request.uri,
                mimeType = "application/xml",
            )
        )
    )
}

fun List<ResolvedThread>.render(): String {
    if( this.isEmpty()) {
        return """
            <threads>
            You're not a participant in any threads. When you are, they will be listed here.
            </threads>
        """.trimIndent()
    }
    val openThreads = this.filter { !it.isClosed }
    val closedThreads = this.filter { it.isClosed }
    return """
        <threads>
        ${/*Displau in more human readable format rather than pure xml */openThreads.joinToString(separator = "\n") { thread ->
            """
                <thread>
                    <name>${thread.name}</name>
                    <id>${thread.id}</id>
                    <messages>
                        ${thread.messages.joinToString(separator = "\n") { message ->
                            with(message) {
                                val timestampFormatted = java.time.Instant.ofEpochMilli(timestamp).toString()
                                "$timestampFormatted [${senderId}] (Mentioning ${mentions.joinToString(", ")}) $content"
                            }
                        }}
                    </messages>
                </thread>
            """.trimIndent()
        
        }}
        ${/*Displau in more human readable format rather than pure xml */closedThreads.joinToString(separator = "\n") { thread ->
            """
                <thread>
                    <name>${thread.name}</name>
                    <id>${thread.id}</id>
                    <messages>
                       (omitted, thread is closed)
                    </messages>
                    <summary>
                        <messageCount>${thread.messages.size}</messageCount>
                        <summaryMessage>${thread.summary}</summaryMessage>
                    </summary>
                </thread>
            """.trimIndent()
        }}
        </threads>
    """.trimIndent()
}
fun CoralAgentIndividualMcp.addMessageResource() {
    addResource(
        name = "message",
        description = "Message resource",
        uri = this.connectedUri,
        mimeType = "application/json",
        readHandler = { request: ReadResourceRequest ->
            handler(request)
        },
    )
}
