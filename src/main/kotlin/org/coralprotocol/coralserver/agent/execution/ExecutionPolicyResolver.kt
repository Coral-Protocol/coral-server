package org.coralprotocol.coralserver.agent.execution

import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.ExecutionPolicyConfig
import org.coralprotocol.coralserver.config.ExecutionTierPolicy
import org.coralprotocol.coralserver.config.OpenShellConfig
import org.coralprotocol.coralserver.config.SandboxConfig

object ExecutionPolicyResolver {
    fun validate(
        declared: ExecutionConfig?,
        policy: ExecutionPolicyConfig,
        source: AgentRegistrySourceIdentifier,
        runtime: RuntimeId,
        trust: ExecutionTrustPolicy,
        openShellConfig: OpenShellConfig,
        sandboxConfig: SandboxConfig,
        fileSystemOptions: Set<String> = emptySet(),
    ): List<ExecutionRejection> = buildList {
        val tier = policy.forSource(source)
        if (declared != null) {
            validateIsolation(declared.minIsolation, tier.maxSupportedIsolation, runtime)
            validateHosts(declared.externalHosts, tier)
        }
        validateRuntime(runtime, tier, trust, openShellConfig, sandboxConfig, fileSystemOptions)
    }

    private fun MutableList<ExecutionRejection>.validateIsolation(
        required: MinIsolation,
        maxSupported: MinIsolation,
        runtime: RuntimeId,
    ) {
        if (required.ordinal > maxSupported.ordinal)
            add(ExecutionRejection.IsolationUnsupported(required, maxSupported))

        if (required == MinIsolation.CONTAINER && !runtime.providesContainerIsolation)
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

    private fun MutableList<ExecutionRejection>.validateRuntime(
        runtime: RuntimeId,
        tier: ExecutionTierPolicy,
        trust: ExecutionTrustPolicy,
        openShellConfig: OpenShellConfig,
        sandboxConfig: SandboxConfig,
        fileSystemOptions: Set<String>,
    ) {
        if (runtime !in tier.allowedRuntimes) {
            add(ExecutionRejection.RuntimeDisabled(runtime, trust.profileName, tier.allowedRuntimes))
            return
        }

        if (runtime == RuntimeId.SANDBOX) {
            if (sandboxConfig.provisionUrl == null)
                add(ExecutionRejection.SandboxUnavailable("sandbox.provision_url (cloud /provision URL) is not configured"))
            // The agent runs off-host, so file-system options would materialise as coral-server-local
            // mount paths the remote VM cannot see — reject up front instead of emitting broken paths.
            if (fileSystemOptions.isNotEmpty())
                add(ExecutionRejection.SandboxFileTransportUnsupported(fileSystemOptions))
            return
        }

        if (runtime != RuntimeId.OPENSHELL) return

        val supervisor = openShellConfig.supervisorPath
        when {
            supervisor == null -> add(ExecutionRejection.SandboxUnavailable("openshell.supervisor_path is not configured"))
            !supervisor.toFile().canExecute() -> add(ExecutionRejection.SandboxUnavailable("openshell supervisor at $supervisor is not executable"))
        }

        val docker = trust.docker
        if (docker.user != null) {
            add(ExecutionRejection.RuntimeIncompatibleWithTrust(
                runtime = runtime,
                profileName = trust.profileName,
                detail = "supervisor must start as root inside the container to drop privileges; profile pins user='${docker.user}'",
            ))
        }
        if (docker.readOnlyRootFilesystem && "/run" !in docker.tmpFs) {
            add(ExecutionRejection.RuntimeIncompatibleWithTrust(
                runtime = runtime,
                profileName = trust.profileName,
                detail = "supervisor writes netns state under /run; profile is read-only without a tmpfs covering /run",
            ))
        }
    }
}
