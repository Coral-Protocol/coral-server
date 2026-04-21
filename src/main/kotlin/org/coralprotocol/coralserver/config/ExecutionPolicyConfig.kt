package org.coralprotocol.coralserver.config

import org.coralprotocol.coralserver.agent.execution.ExecutionTrustTier
import org.coralprotocol.coralserver.agent.execution.MinIsolation

data class ExecutionPolicyConfig(
    val trusted: ExecutionTierPolicy = ExecutionTierPolicy(),
    val marketplace: ExecutionTierPolicy = ExecutionTierPolicy(),
) {
    fun forTier(tier: ExecutionTrustTier): ExecutionTierPolicy = when (tier) {
        ExecutionTrustTier.TRUSTED -> trusted
        ExecutionTrustTier.UNTRUSTED -> marketplace
    }
}

data class ExecutionTierPolicy(
    val maxSupportedIsolation: MinIsolation = MinIsolation.CONTAINER,
    val allowedHosts: Set<String>? = null,
    val deniedHosts: Set<String> = emptySet(),
)
