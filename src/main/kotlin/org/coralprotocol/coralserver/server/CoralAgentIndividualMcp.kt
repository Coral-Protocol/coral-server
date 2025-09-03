package org.coralprotocol.coralserver.server

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import org.coralprotocol.coralserver.mcpresources.addMessageResource
import org.coralprotocol.coralserver.mcptools.addThreadTools
import org.coralprotocol.coralserver.session.CustomTool
import org.coralprotocol.coralserver.session.LocalSession
import org.coralprotocol.coralserver.session.addExtraTool

/**
 * Represents a persistent connection to a Coral agent.
 * Each agent instance has a unique MCP server instance assigned to it.
 *
 * CoralSession
 *
 * This [CoralAgentIndividualMcp] should persist even if the agent reconnects via a different transport.
 */
class CoralAgentIndividualMcp(
    val localSession: LocalSession,
    /**
     * The ID of the agent associated with this connection.
     */
    val connectedAgentId: String,
    val maxWaitForMentionsTimeoutMs: Long = 2000,
    val extraTools: Set<CustomTool> = setOf(),
) : Server(
    Implementation(
        name = "Coral Server",
        version = "0.1.0"
    ),
    ServerOptions(
        capabilities = ServerCapabilities(
            prompts = ServerCapabilities.Prompts(listChanged = true),
            resources = ServerCapabilities.Resources(subscribe = true, listChanged = true),
            tools = ServerCapabilities.Tools(listChanged = true),
        )
    ),
) {
    init {
        addThreadTools()
        addMessageResource()
        //addInstructionResource()
        extraTools.forEach {
            addExtraTool(localSession.id, connectedAgentId, it)
        }
    }

    /**
     * Attaches to the given transport, starts it, and starts listening for messages.
     *
     * The Protocol object assumes ownership of the Transport, replacing any callbacks that have already been set, and expects that it is the only user of the Transport instance going forward.
     */
    override suspend fun connect(transport: Transport) {
        return super.connect(transport)
////        return if (isRemote) {
////            transport.onMessage {
////
////            }
//        } else super.connect(transport)
    }

    suspend fun connectWebscoket() {

    }

    suspend fun closeTransport() {
        transport?.close()
    }
}

