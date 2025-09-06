package org.coralprotocol.coralserver.session.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SessionAgentState {
    @SerialName("disconnected")
    Disconnected,
    @SerialName("connecting")
    Connecting,
    @SerialName("listening")
    Listening,
    @SerialName("busy")
    Busy,
    @SerialName("dead")
    Dead,
}

fun SessionAgentState.isConnected(): Boolean {
    return this == SessionAgentState.Listening || this == SessionAgentState.Busy
}