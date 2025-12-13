package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable

@Serializable
data class RegistryAgentInfo(
    val description: String?,
    val capabilities: Set<AgentCapability>,
    val identifier: RegistryAgentIdentifier
)