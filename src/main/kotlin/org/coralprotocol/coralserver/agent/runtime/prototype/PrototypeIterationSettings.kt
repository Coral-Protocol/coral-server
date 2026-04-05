package org.coralprotocol.coralserver.agent.runtime.prototype

import kotlinx.serialization.Serializable

@Serializable
data class PrototypeIterationSettings(
    val count: Int = 20,
    val delay: Int = 0,
)
