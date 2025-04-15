package org.coralprotocol.agentfuzzyp2ptools.resources

import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext

class MessageService(
    private val server: Server,
    private val coroutineContext: CoroutineContext = Dispatchers.IO + SupervisorJob()
) {
    private val messageResource = MessageResource()
    private val scope = CoroutineScope(coroutineContext)

    init {
        server.addMessageResource(messageResource)
    }

    fun sendMessage(agentId: String, message: String) {
        messageResource.addMessage(agentId, message)
    }

    fun getMessages(agentId: String): List<String> {
        return messageResource.getMessagesForAgent(agentId)
    }

    fun observeMessages(): Flow<Map<String, List<String>>> {
        return messageResource.getMessageUpdatesFlow()
    }
} 