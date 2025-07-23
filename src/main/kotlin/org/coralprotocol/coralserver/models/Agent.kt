package org.coralprotocol.coralserver.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.session.CustomTool

/**
 * Represents an agent in the system.
 */
// TODO: make Agent a data class, when URI's are implemented
@Serializable
class Agent(
    val id: String,
    var description: String = "", // Description of the agent's responsibilities

    var state: AgentState = AgentState.Disconnected,
    var mcpUrl: String?,

    val extraTools: Set<CustomTool> = setOf()
)

@Serializable
enum class AgentState {
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

public fun AgentState.isConnected(): Boolean {
    return this == AgentState.Listening || this == AgentState.Busy
}