package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable

/**
 * Represents an agent in the system.
 */
// TODO: make Agent a data class, when URI's are implemented
@Serializable
class Agent(
    val id: String,
    val description: String = "" // Description of the agent's responsibilities
)