package org.coralprotocol.coralserver.agent.execution

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Capability
import com.github.dockerjava.api.model.HostConfig
import org.coralprotocol.coralserver.logging.LoggingInterface

fun DockerExecutionTrustPolicy.toHostConfig(
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

    if (readOnlyRootFilesystem && tmpFs.isNotEmpty()) {
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

internal fun Set<String>.toCapabilities(logger: LoggingInterface): List<Capability> =
    mapNotNull { capability ->
        runCatching { enumValueOf<Capability>(capability.uppercase()) }
            .onFailure {
                logger.warn { "Unknown Docker capability in config: $capability" }
            }
            .getOrNull()
    }
