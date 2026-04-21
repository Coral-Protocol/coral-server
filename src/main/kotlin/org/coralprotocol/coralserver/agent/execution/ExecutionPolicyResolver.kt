package org.coralprotocol.coralserver.agent.execution

import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.ExecutionPolicyConfig
import org.coralprotocol.coralserver.config.ExecutionTierPolicy

object ExecutionPolicyResolver {
    fun validate(
        declared: ExecutionConfig?,
        policy: ExecutionPolicyConfig,
        trust: ExecutionTrustTier,
        runtime: RuntimeId,
    ): List<ExecutionRejection> {
        declared ?: return emptyList()
        val tier = policy.forTier(trust)
        return buildList {
            validateIsolation(declared.minIsolation, tier.maxSupportedIsolation, runtime)
            validateHosts(declared.network.externalHosts, tier)
        }
    }

    private fun MutableList<ExecutionRejection>.validateIsolation(
        required: MinIsolation,
        maxSupported: MinIsolation,
        runtime: RuntimeId,
    ) {
        if (required.ordinal > maxSupported.ordinal)
            add(ExecutionRejection.IsolationUnsupported(required, maxSupported))

        if (required == MinIsolation.CONTAINER && runtime != RuntimeId.DOCKER)
            add(ExecutionRejection.IsolationIncompatibleWithRuntime(required, runtime))
    }

    private fun MutableList<ExecutionRejection>.validateHosts(
        hosts: Set<String>,
        tier: ExecutionTierPolicy,
    ) {
        hosts.forEach { host ->
            val denied = host in tier.deniedHosts
            val notAllowlisted = tier.allowedHosts != null && host !in tier.allowedHosts
            if (denied || notAllowlisted) add(ExecutionRejection.HostDenied(host))
        }
    }
}
