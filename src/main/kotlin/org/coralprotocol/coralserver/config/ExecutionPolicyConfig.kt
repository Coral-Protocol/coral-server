package org.coralprotocol.coralserver.config

import org.coralprotocol.coralserver.agent.execution.MinIsolation
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier

data class ExecutionPolicyConfig(
    val trusted: ExecutionTierPolicy = ExecutionTierPolicy(),
    val marketplace: ExecutionTierPolicy = ExecutionTierPolicy(),
) {
    fun forSource(source: AgentRegistrySourceIdentifier): ExecutionTierPolicy = when (source) {
        is AgentRegistrySourceIdentifier.Local -> trusted
        is AgentRegistrySourceIdentifier.Marketplace,
        is AgentRegistrySourceIdentifier.Linked -> marketplace
    }
}

data class ExecutionTierPolicy(
    val maxSupportedIsolation: MinIsolation = MinIsolation.CONTAINER,
    val allowedHosts: Set<String>? = null,
    val deniedHosts: Set<String> = emptySet(),
)
