package org.coralprotocol.coralserver.agent.execution

import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.CloudConfig
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
        cloudConfig: CloudConfig,
        fileSystemOptions: Set<String> = emptySet(),
    ): List<ExecutionRejection> = buildList {
        val tier = policy.forSource(source)
        if (declared != null) {
            validateIsolation(declared.minIsolation, tier.maxSupportedIsolation, runtime)
            validateHosts(declared.externalHosts, tier)
        }
        validateRuntime(runtime, tier, trust, openShellConfig, sandboxConfig, cloudConfig, fileSystemOptions)
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
        // Match on the host component so a `:port` suffix can't slip past a bare-host deny entry (and a
        // bare-host allow entry isn't defeated by one). Egress is enforced at domain granularity anyway.
        val denied = tier.deniedHosts.mapTo(mutableSetOf()) { it.egressHost() }
        val allowed = tier.allowedHosts?.mapTo(mutableSetOf()) { it.egressHost() }
        hosts.forEach { host ->
            val h = host.egressHost()
            if (h in denied || (allowed != null && h !in allowed)) add(ExecutionRejection.HostDenied(host))
        }
    }

    private fun MutableList<ExecutionRejection>.validateRuntime(
        runtime: RuntimeId,
        tier: ExecutionTierPolicy,
        trust: ExecutionTrustPolicy,
        openShellConfig: OpenShellConfig,
        sandboxConfig: SandboxConfig,
        cloudConfig: CloudConfig,
        fileSystemOptions: Set<String>,
    ) {
        if (runtime !in tier.allowedRuntimes) {
            add(ExecutionRejection.RuntimeDisabled(runtime, trust.profileName, tier.allowedRuntimes))
            return
        }

        if (runtime == RuntimeId.SANDBOX) {
            // provision_url + agent_gateway_url are interdependent: without the gateway, resolveBaseUrl
            // has no callback base and the agent could never reach coral-server. Gate both, plus a key.
            if (sandboxConfig.provisionUrl == null)
                add(ExecutionRejection.SandboxUnavailable("sandbox.provision_url (cloud /provision URL) is not configured"))
            if (sandboxConfig.agentGatewayUrl == null)
                add(ExecutionRejection.SandboxUnavailable("sandbox.agent_gateway_url (cloud gateway the agent connects back through) is not configured"))
            if (sandboxConfig.apiKey == null && cloudConfig.apiKey == null)
                add(ExecutionRejection.SandboxUnavailable("sandbox.api_key / cloud.api_key (bearer for /provision) is not configured"))
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
