package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable

@Serializable
data class AgentLlmConfig(
    val proxies: List<AgentLlmProxy> = emptyList()
)

@Serializable
data class AgentLlmProxy(
    val name: String,
    val format: String,
    val model: String? = null
)
