package org.coralprotocol.coralserver.agent.execution

import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.HostConfig
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.config.DockerConfig
import org.coralprotocol.coralserver.logging.LoggingInterface

data class DockerExecutionTrustPolicy(
    val readOnlyRootFilesystem: Boolean = false,
    val noNewPrivileges: Boolean = true,
    val dropCapabilities: Set<String> = setOf("ALL"),
    // bounds fork-bomb blast radius; 256 PIDs covers typical agent process counts
    val pidsLimit: Long? = 256,
    val nanoCpus: Long? = null,
    val memoryLimitBytes: Long? = null,
    val user: String? = null,
    val tmpFs: Map<String, String> = emptyMap(),
    val requireImageDigest: Boolean = false,
) {
    val requiresWritableTmpHome: Boolean
        get() = readOnlyRootFilesystem || user != null
}

data class ExecutionTrustPolicy(
    val profileName: String,
    val docker: DockerExecutionTrustPolicy,
)

fun AgentRegistrySourceIdentifier.resolveTrustPolicy(
    dockerConfig: DockerConfig,
): ExecutionTrustPolicy = when (this) {
    is AgentRegistrySourceIdentifier.Local -> ExecutionTrustPolicy(
        profileName = "trusted_local",
        docker = dockerConfig.trusted,
    )
    is AgentRegistrySourceIdentifier.Marketplace,
    is AgentRegistrySourceIdentifier.Linked -> ExecutionTrustPolicy(
        profileName = "marketplace_untrusted",
        docker = dockerConfig.marketplace,
    )
}

fun DockerExecutionTrustPolicy.buildHostConfig(
    volumes: List<Bind>,
    logger: LoggingInterface,
    extraCaps: List<Capability> = emptyList(),
): HostConfig {
    val hostConfig = HostConfig()
        .withBinds(volumes)
        .withPrivileged(false)
        .withReadonlyRootfs(readOnlyRootFilesystem)

    if (noNewPrivileges) {
        hostConfig.withSecurityOpts(listOf("no-new-privileges"))
    }

    if (tmpFs.isNotEmpty()) {
        hostConfig.withTmpFs(tmpFs)
    }

    if (dropCapabilities.isNotEmpty()) {
        hostConfig.withCapDrop(*dropCapabilities.toCapabilities(logger).toTypedArray())
    }

    if (extraCaps.isNotEmpty()) {
        hostConfig.withCapAdd(*extraCaps.toTypedArray())
    }

    pidsLimit?.let { hostConfig.withPidsLimit(it) }
    nanoCpus?.let { hostConfig.withNanoCPUs(it) }
    memoryLimitBytes?.let { hostConfig.withMemory(it) }

    return hostConfig
}

fun DockerExecutionTrustPolicy.applyTo(
    cmd: CreateContainerCmd,
    volumes: List<Bind>,
    logger: LoggingInterface,
    extraCaps: List<Capability> = emptyList(),
) {
    cmd.withHostConfig(buildHostConfig(volumes, logger, extraCaps))
    user?.takeIf { it.isNotBlank() }?.let { cmd.withUser(it) }
}

fun DockerExecutionTrustPolicy.sanitizeImage(
    imageName: String,
    id: RegistryAgentIdentifier,
    profileName: String,
    logger: LoggingInterface,
): String {
    if (imageName.contains("@sha256:")) {
        return imageName
    }

    if (requireImageDigest) {
        throw IllegalArgumentException(
            "Docker image $imageName must be pinned by digest (@sha256:...) by execution profile '$profileName'"
        )
    }

    if (imageName.contains(":")) {
        if (!imageName.endsWith(":${id.version}")) {
            logger.warn { "Image $imageName does not match the agent version: ${id.version}" }
        }

        return imageName
    }

    return "$imageName:${id.version}"
}

private fun Set<String>.toCapabilities(logger: LoggingInterface): List<Capability> =
    mapNotNull { capability ->
        runCatching { enumValueOf<Capability>(capability.uppercase()) }
            .onFailure { logger.warn { "Unknown Docker capability in config: $capability" } }
            .getOrNull()
    }
