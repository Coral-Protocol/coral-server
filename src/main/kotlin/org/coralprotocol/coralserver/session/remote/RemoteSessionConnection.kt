package org.coralprotocol.coralserver.session.remote

import io.ktor.client.plugins.websocket.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.server.apiJsonConfig
import org.coralprotocol.coralserver.session.LocalSession

suspend fun ClientWebSocketSession.createRemoteSessionClient(session: LocalSession, agentName: String) {
    val mcp = CoralAgentIndividualMcp(session, agentName)
    mcp.connect(RemoteSessionConnectionClient(this))
}

suspend fun WebSocketServerSession.createRemoteSessionServer(remoteSessionManager: RemoteSessionManager) {
    val claimId = call.parameters["claimId"]!!

    // executeClaim will orchestrate the requested agent...
    val remoteSession = remoteSessionManager.executeClaim(claimId)

    // ... when the requested agent launches, the SSE end point will be hit and complete this CompletableDeferred
    val sseTransport = remoteSession.deferredMcpTransport.await()

    val server = RemoteSessionConnectionServer(this, sseTransport)
    server.start()

    remoteSession.destroy()
}

internal fun Frame.Text.toSessionFrame(): RemoteSessionFrame =
    apiJsonConfig.decodeFromString(this.data.decodeToString())

internal fun JSONRPCMessage.toWsFrame(): Frame.Text =
    Frame.Text(apiJsonConfig.encodeToString(RemoteSessionFrame.serializer(), RemoteSessionFrame.Sse(this)))