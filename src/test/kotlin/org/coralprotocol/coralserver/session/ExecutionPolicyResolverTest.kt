package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.coralprotocol.coralserver.agent.execution.DockerExecutionTrustPolicy
import org.coralprotocol.coralserver.agent.execution.ExecutionConfig
import org.coralprotocol.coralserver.agent.execution.ExecutionPolicyResolver
import org.coralprotocol.coralserver.agent.execution.ExecutionRejection
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustPolicy
import org.coralprotocol.coralserver.agent.execution.MinIsolation
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.CloudConfig
import org.coralprotocol.coralserver.config.ExecutionPolicyConfig
import org.coralprotocol.coralserver.config.ExecutionTierPolicy
import org.coralprotocol.coralserver.config.OpenShellConfig
import org.coralprotocol.coralserver.config.SandboxConfig
import java.nio.file.Paths

class ExecutionPolicyResolverTest : FunSpec({

    val trustedProfile = ExecutionTrustPolicy(
        profileName = "trusted_local",
        docker = DockerExecutionTrustPolicy(),
    )

    val marketplaceProfile = ExecutionTrustPolicy(
        profileName = "marketplace_untrusted",
        docker = DockerExecutionTrustPolicy(
            readOnlyRootFilesystem = true,
            user = "65532:65532",
            tmpFs = mapOf("/tmp" to "rw,noexec,nosuid,nodev,size=64m"),
        ),
    )

    val availableSupervisor = OpenShellConfig(supervisorPath = Paths.get("/bin/sh"))
    val missingSupervisor = OpenShellConfig(supervisorPath = null)

    val configuredSandbox = SandboxConfig(
        provisionUrl = "https://cloud.example.com/api/internal/coral/provision",
        agentGatewayUrl = "https://cloud.example.com/sandbox",
        apiKey = "provision-key",
    )
    val unconfiguredSandbox = SandboxConfig()

    fun validate(
        declared: ExecutionConfig?,
        policy: ExecutionPolicyConfig = ExecutionPolicyConfig(),
        source: AgentRegistrySourceIdentifier = AgentRegistrySourceIdentifier.Local,
        runtime: RuntimeId = RuntimeId.DOCKER,
        trust: ExecutionTrustPolicy = trustedProfile,
        openShellConfig: OpenShellConfig = availableSupervisor,
        sandboxConfig: SandboxConfig = configuredSandbox,
        cloudConfig: CloudConfig = CloudConfig(),
        fileSystemOptions: Set<String> = emptySet(),
    ) = ExecutionPolicyResolver.validate(
        declared, policy, source, runtime, trust, openShellConfig, sandboxConfig, cloudConfig, fileSystemOptions,
    )

    test("missingDeclarationSkipsValidation") {
        validate(declared = null).shouldBeEmpty()
    }

    test("declarationPassesThroughWhenPolicyIsPermissive") {
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            externalHosts = setOf("api.firecrawl.dev"),
        )
        validate(declared).shouldBeEmpty()
    }

    test("containerDeclarationOnNonDockerRuntimeIsRejected") {
        val declared = ExecutionConfig(minIsolation = MinIsolation.CONTAINER)
        validate(declared, runtime = RuntimeId.EXECUTABLE) shouldBe listOf(
            ExecutionRejection.IsolationIncompatibleWithRuntime(MinIsolation.CONTAINER, RuntimeId.EXECUTABLE)
        )
    }

    test("containerDeclarationOnOpenShellRuntimeIsAccepted") {
        val declared = ExecutionConfig(minIsolation = MinIsolation.CONTAINER)
        validate(declared, runtime = RuntimeId.OPENSHELL).shouldBeEmpty()
    }

    test("isolationBeyondOperatorCeilingIsRejected") {
        val policy = ExecutionPolicyConfig(
            marketplace = ExecutionTierPolicy(maxSupportedIsolation = MinIsolation.PROCESS)
        )
        val declared = ExecutionConfig(minIsolation = MinIsolation.CONTAINER)
        validate(
            declared, policy, AgentRegistrySourceIdentifier.Marketplace, trust = marketplaceProfile,
        ) shouldContainExactly listOf(
            ExecutionRejection.IsolationUnsupported(MinIsolation.CONTAINER, MinIsolation.PROCESS)
        )
    }

    test("hostsInDenylistAreRejected") {
        val policy = ExecutionPolicyConfig(
            marketplace = ExecutionTierPolicy(deniedHosts = setOf("evil.example.com"))
        )
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            externalHosts = setOf("api.firecrawl.dev", "evil.example.com"),
        )
        validate(declared, policy, AgentRegistrySourceIdentifier.Marketplace, trust = marketplaceProfile) shouldContainExactly listOf(
            ExecutionRejection.HostDenied("evil.example.com")
        )
    }

    test("hostsOutsideAllowlistAreRejected") {
        val policy = ExecutionPolicyConfig(
            marketplace = ExecutionTierPolicy(allowedHosts = setOf("api.firecrawl.dev"))
        )
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            externalHosts = setOf("api.firecrawl.dev", "other.example.com"),
        )
        validate(declared, policy, AgentRegistrySourceIdentifier.Marketplace, trust = marketplaceProfile) shouldContainExactly listOf(
            ExecutionRejection.HostDenied("other.example.com")
        )
    }

    test("operatorPolicyAppliesPerSource") {
        val policy = ExecutionPolicyConfig(
            trusted = ExecutionTierPolicy(allowedHosts = null),
            marketplace = ExecutionTierPolicy(allowedHosts = setOf("api.firecrawl.dev")),
        )
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            externalHosts = setOf("other.example.com"),
        )
        validate(declared, policy, AgentRegistrySourceIdentifier.Local).shouldBeEmpty()
        validate(declared, policy, AgentRegistrySourceIdentifier.Marketplace, trust = marketplaceProfile) shouldContainExactly listOf(
            ExecutionRejection.HostDenied("other.example.com")
        )
    }

    test("openShellRuntimeRejectedWhenTrustProfilePinsUser") {
        val userOnly = ExecutionTrustPolicy(
            profileName = "marketplace_untrusted",
            docker = DockerExecutionTrustPolicy(user = "65532:65532"),
        )
        validate(
            declared = null,
            runtime = RuntimeId.OPENSHELL,
            trust = userOnly,
        ) shouldContainExactly listOf(
            ExecutionRejection.RuntimeIncompatibleWithTrust(
                runtime = RuntimeId.OPENSHELL,
                profileName = "marketplace_untrusted",
                detail = "supervisor must start as root inside the container to drop privileges; profile pins user='65532:65532'",
            )
        )
    }

    test("openShellRuntimeRejectedWhenReadOnlyRootfsHasNoRunTmpfs") {
        val noRunTmpfs = ExecutionTrustPolicy(
            profileName = "marketplace_untrusted",
            docker = DockerExecutionTrustPolicy(
                readOnlyRootFilesystem = true,
                tmpFs = mapOf("/tmp" to "rw"),
            ),
        )
        validate(
            declared = null,
            runtime = RuntimeId.OPENSHELL,
            trust = noRunTmpfs,
        ) shouldContainExactly listOf(
            ExecutionRejection.RuntimeIncompatibleWithTrust(
                runtime = RuntimeId.OPENSHELL,
                profileName = "marketplace_untrusted",
                detail = "supervisor writes netns state under /run; profile is read-only without a tmpfs covering /run",
            )
        )
    }

    test("openShellRuntimeAcceptedWithRunTmpfs") {
        val trustWithRun = ExecutionTrustPolicy(
            profileName = "openshell_marketplace",
            docker = DockerExecutionTrustPolicy(
                readOnlyRootFilesystem = true,
                tmpFs = mapOf("/tmp" to "rw", "/run" to "rw"),
            ),
        )
        validate(
            declared = null,
            runtime = RuntimeId.OPENSHELL,
            trust = trustWithRun,
        ).shouldBeEmpty()
    }

    test("openShellRuntimeRejectedWhenSupervisorMissing") {
        validate(
            declared = null,
            runtime = RuntimeId.OPENSHELL,
            openShellConfig = missingSupervisor,
        ) shouldContainExactly listOf(
            ExecutionRejection.SandboxUnavailable("openshell.supervisor_path is not configured")
        )
    }

    test("openShellRuntimeRejectedWhenSupervisorNotExecutable") {
        val notExecutable = OpenShellConfig(supervisorPath = Paths.get("/does/not/exist"))
        validate(
            declared = null,
            runtime = RuntimeId.OPENSHELL,
            openShellConfig = notExecutable,
        ) shouldContainExactly listOf(
            ExecutionRejection.SandboxUnavailable("openshell supervisor at /does/not/exist is not executable")
        )
    }

    test("defaultMarketplaceTierRejectsExecutableRuntime") {
        validate(
            declared = null,
            source = AgentRegistrySourceIdentifier.Marketplace,
            runtime = RuntimeId.EXECUTABLE,
            trust = marketplaceProfile,
        ) shouldContainExactly listOf(
            ExecutionRejection.RuntimeDisabled(
                runtime = RuntimeId.EXECUTABLE,
                profileName = "marketplace_untrusted",
                allowedRuntimes = setOf(RuntimeId.DOCKER, RuntimeId.OPENSHELL, RuntimeId.SANDBOX),
            )
        )
    }

    test("defaultTrustedTierAllowsExecutableRuntime") {
        validate(
            declared = null,
            source = AgentRegistrySourceIdentifier.Local,
            runtime = RuntimeId.EXECUTABLE,
        ).shouldBeEmpty()
    }

    test("operatorOverrideAllowsExecutableOnMarketplace") {
        val policy = ExecutionPolicyConfig(
            marketplace = ExecutionTierPolicy(
                allowedRuntimes = setOf(RuntimeId.DOCKER, RuntimeId.OPENSHELL, RuntimeId.EXECUTABLE),
            )
        )
        validate(
            declared = null,
            policy = policy,
            source = AgentRegistrySourceIdentifier.Marketplace,
            runtime = RuntimeId.EXECUTABLE,
            trust = marketplaceProfile,
        ).shouldBeEmpty()
    }

    test("operatorOverrideRestrictsTrustedTierToDocker") {
        val policy = ExecutionPolicyConfig(
            trusted = ExecutionTierPolicy(allowedRuntimes = setOf(RuntimeId.DOCKER)),
        )
        validate(
            declared = null,
            policy = policy,
            source = AgentRegistrySourceIdentifier.Local,
            runtime = RuntimeId.EXECUTABLE,
        ) shouldContainExactly listOf(
            ExecutionRejection.RuntimeDisabled(
                runtime = RuntimeId.EXECUTABLE,
                profileName = "trusted_local",
                allowedRuntimes = setOf(RuntimeId.DOCKER),
            )
        )
    }

    test("runtimeDisabledShortCircuitsOpenShellChecks") {
        val policy = ExecutionPolicyConfig(
            marketplace = ExecutionTierPolicy(allowedRuntimes = setOf(RuntimeId.DOCKER)),
        )
        validate(
            declared = null,
            policy = policy,
            source = AgentRegistrySourceIdentifier.Marketplace,
            runtime = RuntimeId.OPENSHELL,
            trust = marketplaceProfile,
            openShellConfig = missingSupervisor,
        ) shouldContainExactly listOf(
            ExecutionRejection.RuntimeDisabled(
                runtime = RuntimeId.OPENSHELL,
                profileName = "marketplace_untrusted",
                allowedRuntimes = setOf(RuntimeId.DOCKER),
            )
        )
    }

    test("sandboxRuntimeRejectedWhenUnconfigured") {
        validate(
            declared = null,
            source = AgentRegistrySourceIdentifier.Marketplace,
            runtime = RuntimeId.SANDBOX,
            trust = marketplaceProfile,
            sandboxConfig = unconfiguredSandbox,
        ) shouldContainExactlyInAnyOrder listOf(
            ExecutionRejection.SandboxUnavailable("sandbox.provision_url (cloud /provision URL) is not configured"),
            ExecutionRejection.SandboxUnavailable("sandbox.agent_gateway_url (cloud gateway the agent connects back through) is not configured"),
            ExecutionRejection.SandboxUnavailable("sandbox.api_key / cloud.api_key (bearer for /provision) is not configured"),
        )
    }

    test("sandboxRuntimeRejectedWhenGatewayMissing") {
        validate(
            declared = null,
            source = AgentRegistrySourceIdentifier.Marketplace,
            runtime = RuntimeId.SANDBOX,
            trust = marketplaceProfile,
            sandboxConfig = configuredSandbox.copy(agentGatewayUrl = null),
        ) shouldContainExactly listOf(
            ExecutionRejection.SandboxUnavailable("sandbox.agent_gateway_url (cloud gateway the agent connects back through) is not configured")
        )
    }

    test("sandboxRuntimeApiKeyFallsBackToCloudConfig") {
        validate(
            declared = ExecutionConfig(minIsolation = MinIsolation.CONTAINER),
            source = AgentRegistrySourceIdentifier.Marketplace,
            runtime = RuntimeId.SANDBOX,
            trust = marketplaceProfile,
            sandboxConfig = configuredSandbox.copy(apiKey = null),
            cloudConfig = CloudConfig(apiKey = "cloud-key"),
        ).shouldBeEmpty()
    }

    test("sandboxRuntimeRejectedWhenNoApiKeyAnywhere") {
        validate(
            declared = null,
            source = AgentRegistrySourceIdentifier.Marketplace,
            runtime = RuntimeId.SANDBOX,
            trust = marketplaceProfile,
            sandboxConfig = configuredSandbox.copy(apiKey = null),
            cloudConfig = CloudConfig(apiKey = null),
        ) shouldContainExactly listOf(
            ExecutionRejection.SandboxUnavailable("sandbox.api_key / cloud.api_key (bearer for /provision) is not configured")
        )
    }

    test("sandboxRuntimeAcceptedWhenConfigured") {
        validate(
            declared = ExecutionConfig(minIsolation = MinIsolation.CONTAINER),
            source = AgentRegistrySourceIdentifier.Marketplace,
            runtime = RuntimeId.SANDBOX,
            trust = marketplaceProfile,
            sandboxConfig = configuredSandbox,
        ).shouldBeEmpty()
    }

    test("sandboxRuntimeRejectsFileSystemOptions") {
        validate(
            declared = null,
            source = AgentRegistrySourceIdentifier.Marketplace,
            runtime = RuntimeId.SANDBOX,
            trust = marketplaceProfile,
            sandboxConfig = configuredSandbox,
            fileSystemOptions = setOf("config_blob"),
        ) shouldContainExactly listOf(
            ExecutionRejection.SandboxFileTransportUnsupported(setOf("config_blob"))
        )
    }

    test("denylistMatchesHostRegardlessOfDeclaredPort") {
        val policy = ExecutionPolicyConfig(
            marketplace = ExecutionTierPolicy(deniedHosts = setOf("evil.example.com"))
        )
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            externalHosts = setOf("evil.example.com:443"),
        )
        validate(
            declared, policy, AgentRegistrySourceIdentifier.Marketplace, trust = marketplaceProfile,
        ) shouldContainExactly listOf(
            ExecutionRejection.HostDenied("evil.example.com:443")
        )
    }
})
