//package org.coralprotocol.coralserver.session.remote
//
//import io.ktor.websocket.*
//import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
//import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
//
//class RemoteSessionConnectionClient(
//    private val session: WebSocketSession
//) : AbstractTransport() {
//    override suspend fun start() {
//        TODO()
////        logger.debug { "Starting RemoteSessionClient" }
////
////        for (frame in session.incoming) {
////            if (frame !is Frame.Text)
////                continue
////
////            when (val rsf = frame.toSessionFrame(json)) {
////                is RemoteSessionFrame.Sse -> _onMessage(rsf.message)
////            }
////        }
//    }
//
//    override suspend fun send(message: JSONRPCMessage) {
//        TODO()
////        val frame = message.toWsFrame(json)
////        session.outgoing.send(frame)
//    }
//
//    override suspend fun close() {
//        //logger.debug { "Closing RemoteSessionClient" }
//
//        session.close()
//    }
//}