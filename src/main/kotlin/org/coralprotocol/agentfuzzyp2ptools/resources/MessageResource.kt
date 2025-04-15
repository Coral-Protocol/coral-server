package org.coralprotocol.agentfuzzyp2ptools.resources

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

class MessageResource {
    private val messages = ConcurrentHashMap<String, List<String>>()
    private val messageUpdates = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    fun addMessage(agentId: String, message: String) {
        val currentMessages = messages.getOrDefault(agentId, emptyList())
        val updatedMessages = currentMessages + message
        messages[agentId] = updatedMessages
        messageUpdates.value = messages.toMap()
    }

    fun getMessagesForAgent(agentId: String): List<String> {
        return messages.getOrDefault(agentId, emptyList())
    }

    fun getMessageUpdatesFlow() = messageUpdates.asStateFlow()
}

/**
 * Extension function to add the message resource to a server.
 */
fun Server.addMessageResource(messageResource: MessageResource) {
    addResource(
        uri = "mcp://messages",
        name = "Agent Messages",
        description = "Live updating message resource for agents",
        mimeType = "application/json"
    ) { request: ReadResourceRequest ->
        try {
            val uri = java.net.URI(request.uri)
            val query = uri.query ?: ""
            val queryParams = query.split('&')
                .filter { it.isNotEmpty() }
                .associate { param ->
                    val parts = param.split('=', limit = 2)
                    if (parts.size == 2) {
                        parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
                    } else {
                        parts[0] to ""
                    }
                }
            
            val agentId = queryParams["agentId"]
            if (agentId == null) {
                ReadResourceResult(
                    contents = listOf(
                        TextResourceContents(
                            text = "Error: agentId parameter is required",
                            uri = request.uri,
                            mimeType = "text/plain"
                        )
                    )
                )
            } else {
                val agentMessages = messageResource.getMessagesForAgent(agentId)
                ReadResourceResult(
                    contents = listOf(
                        TextResourceContents(
                            text = agentMessages.joinToString("\n"),
                            uri = request.uri,
                            mimeType = "text/plain"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents(
                        text = "Error parsing request: ${e.message}",
                        uri = request.uri,
                        mimeType = "text/plain"
                    )
                )
            )
        }
    }
} 