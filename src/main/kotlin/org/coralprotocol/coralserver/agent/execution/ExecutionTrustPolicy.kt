package org.coralprotocol.coralserver.agent.execution

import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.HostConfig
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.registry.RegistryAgentIdentifier
import org.coralprotocol.coralserver.config.DockerConfig
import org.coralprotocol.coralserver.config.SecurityConfig
import org.coralprotocol.coralserver.logging.LoggingInterface

@Serializable
enum class ExecutionTrustTier {
    TRUSTED,
    UNTRUSTED,
}

data class DockerExecutionTrustPolicy(
    val readOnlyRootFilesystem: Boolean = false,
    val noNewPrivileges: Boolean = true,
    val dropCapabilities: Set<String> = setOf("ALL"),
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
    val trustTier: ExecutionTrustTier,
    val allowExecutableRuntime: Boolean,
    val docker: DockerExecutionTrustPolicy,
) {
    val profileName: String = when (trustTier) {
        ExecutionTrustTier.TRUSTED -> "trusted_local"
        ExecutionTrustTier.UNTRUSTED -> "marketplace_untrusted"
    }
}

fun AgentRegistrySourceIdentifier.resolveTrustPolicy(
    dockerConfig: DockerConfig,
    securityConfig: SecurityConfig,
): ExecutionTrustPolicy = when (this) {
    is AgentRegistrySourceIdentifier.Local -> ExecutionTrustPolicy(
        trustTier = ExecutionTrustTier.TRUSTED,
        allowExecutableRuntime = true,
        docker = dockerConfig.trusted,
    )
    is AgentRegistrySourceIdentifier.Marketplace,
    is AgentRegistrySourceIdentifier.Linked -> ExecutionTrustPolicy(
        trustTier = ExecutionTrustTier.UNTRUSTED,
        allowExecutableRuntime = securityConfig.allowUntrustedExecutableRuntime,
        docker = dockerConfig.marketplace,
    )
}

fun DockerExecutionTrustPolicy.buildHostConfig(
    volumes: List<Bind>,
    logger: LoggingInterface,
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

    pidsLimit?.let { hostConfig.withPidsLimit(it) }
    nanoCpus?.let { hostConfig.withNanoCPUs(it) }
    memoryLimitBytes?.let { hostConfig.withMemory(it) }

    return hostConfig
}

fun DockerExecutionTrustPolicy.applyTo(
    cmd: CreateContainerCmd,
    volumes: List<Bind>,
    logger: LoggingInterface,
) {
    cmd.withHostConfig(buildHostConfig(volumes, logger))
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
