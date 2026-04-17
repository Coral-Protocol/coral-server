package org.coralprotocol.coralserver.agent.execution

import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.config.DockerConfig
import org.coralprotocol.coralserver.config.SecurityConfig

class ExecutionTrustPolicyResolver(
    private val securityConfig: SecurityConfig,
    private val dockerConfig: DockerConfig,
) {
    fun resolve(registrySourceId: AgentRegistrySourceIdentifier): ExecutionTrustPolicy =
        when (registrySourceId) {
            is AgentRegistrySourceIdentifier.Marketplace -> marketplacePolicy()
            is AgentRegistrySourceIdentifier.Local -> trustedLocalPolicy("trusted_local")
            is AgentRegistrySourceIdentifier.Linked -> trustedLocalPolicy("linked")
        }

    private fun trustedLocalPolicy(profileName: String) = ExecutionTrustPolicy(
        profileName = profileName,
        trustTier = ExecutionTrustTier.TRUSTED,
        allowExecutableRuntime = true,
        docker = DockerExecutionTrustPolicy(
            readOnlyRootFilesystem = dockerConfig.readOnlyRootFilesystem,
            noNewPrivileges = dockerConfig.noNewPrivileges,
            dropCapabilities = dockerConfig.dropCapabilities,
            pidsLimit = dockerConfig.pidsLimit,
            nanoCpus = dockerConfig.nanoCpus,
            memoryLimitBytes = dockerConfig.memoryLimitBytes,
            user = dockerConfig.user,
            tmpFs = dockerConfig.tmpFs,
            requireImageDigest = false,
        )
    )

    private fun marketplacePolicy() = ExecutionTrustPolicy(
        profileName = "marketplace_untrusted",
        trustTier = ExecutionTrustTier.UNTRUSTED,
        allowExecutableRuntime = securityConfig.allowMarketplaceExecutableRuntime,
        docker = DockerExecutionTrustPolicy(
            readOnlyRootFilesystem = dockerConfig.readOnlyRootFilesystem || dockerConfig.marketplaceReadOnlyRootFilesystem,
            noNewPrivileges = dockerConfig.noNewPrivileges,
            dropCapabilities = dockerConfig.dropCapabilities,
            pidsLimit = dockerConfig.marketplacePidsLimit ?: dockerConfig.pidsLimit,
            nanoCpus = dockerConfig.marketplaceNanoCpus ?: dockerConfig.nanoCpus,
            memoryLimitBytes = dockerConfig.marketplaceMemoryLimitBytes ?: dockerConfig.memoryLimitBytes,
            user = dockerConfig.marketplaceUser ?: dockerConfig.user,
            tmpFs = dockerConfig.marketplaceTmpFs ?: dockerConfig.tmpFs,
            requireImageDigest = securityConfig.requireMarketplaceDockerImageDigest,
        )
    )
}
