@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session.remote

import io.modelcontextprotocol.kotlin.sdk.JSONRPCMessage
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable
@JsonClassDiscriminator("type")
internal sealed class RemoteSessionFrame {
    @Serializable
    @SerialName("sse")
    data class Sse(
        val message: JSONRPCMessage
    ) : RemoteSessionFrame()
}