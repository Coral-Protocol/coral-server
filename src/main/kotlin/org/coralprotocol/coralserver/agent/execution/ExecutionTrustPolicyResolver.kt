package org.coralprotocol.coralserver.agent.execution

import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.config.DockerConfig
import org.coralprotocol.coralserver.config.DockerTierDefaults
import org.coralprotocol.coralserver.config.SecurityConfig

class ExecutionTrustPolicyResolver(
    private val securityConfig: SecurityConfig,
    private val dockerConfig: DockerConfig,
) {
    fun resolve(registrySourceId: AgentRegistrySourceIdentifier): ExecutionTrustPolicy =
        when (registrySourceId) {
            is AgentRegistrySourceIdentifier.Local -> trustedLocalPolicy()
            is AgentRegistrySourceIdentifier.Marketplace,
            is AgentRegistrySourceIdentifier.Linked -> marketplacePolicy()
        }

    private fun trustedLocalPolicy() = ExecutionTrustPolicy(
        profileName = "trusted_local",
        trustTier = ExecutionTrustTier.TRUSTED,
        allowExecutableRuntime = true,
        docker = dockerPolicyFor(dockerConfig.trusted, requireImageDigest = false),
    )

    private fun marketplacePolicy() = ExecutionTrustPolicy(
        profileName = "marketplace_untrusted",
        trustTier = ExecutionTrustTier.UNTRUSTED,
        allowExecutableRuntime = securityConfig.allowMarketplaceExecutableRuntime,
        docker = dockerPolicyFor(
            tier = dockerConfig.marketplace,
            requireImageDigest = securityConfig.requireMarketplaceDockerImageDigest,
        ),
    )

    private fun dockerPolicyFor(tier: DockerTierDefaults, requireImageDigest: Boolean) =
        DockerExecutionTrustPolicy(
            readOnlyRootFilesystem = tier.readOnlyRootFilesystem,
            noNewPrivileges = dockerConfig.noNewPrivileges,
            dropCapabilities = dockerConfig.dropCapabilities,
            pidsLimit = tier.pidsLimit,
            nanoCpus = tier.nanoCpus,
            memoryLimitBytes = tier.memoryLimitBytes,
            user = tier.user,
            tmpFs = tier.tmpFs,
            requireImageDigest = requireImageDigest,
        )
}
