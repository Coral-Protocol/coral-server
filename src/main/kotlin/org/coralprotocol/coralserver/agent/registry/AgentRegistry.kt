package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable

@Serializable
data class AgentRegistry(
    val importedAgents: Map<String, RegistryAgent> = mapOf(),
    val exportedAgents: Map<String, AgentExport> = mapOf()
)