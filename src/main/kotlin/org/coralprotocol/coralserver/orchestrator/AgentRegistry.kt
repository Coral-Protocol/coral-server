package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.Serializable

@Serializable
data class AgentRegistry(
    val importedAgents: Map<String, RegistryAgent>,
    val exportedAgents: Map<String, AgentExport>
)