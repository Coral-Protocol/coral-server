package org.coralprotocol.coralserver.agent.registry.option

import kotlinx.serialization.Serializable

@Serializable
data class AgentOptionDisplay(
    val label: String? = null,
    val description: String? = null,
    val group: String? = null,
    val multiline: Boolean? = false
)