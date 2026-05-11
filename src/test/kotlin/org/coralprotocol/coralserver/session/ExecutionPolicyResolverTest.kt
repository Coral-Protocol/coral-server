package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.coralprotocol.coralserver.agent.execution.DockerExecutionTrustPolicy
import org.coralprotocol.coralserver.agent.execution.ExecutionConfig
import org.coralprotocol.coralserver.agent.execution.ExecutionPolicyResolver
import org.coralprotocol.coralserver.agent.execution.ExecutionRejection
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustPolicy
import org.coralprotocol.coralserver.agent.execution.MinIsolation
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.ExecutionPolicyConfig
import org.coralprotocol.coralserver.config.ExecutionTierPolicy
import org.coralprotocol.coralserver.config.OpenShellConfig
import java.nio.file.Paths

class ExecutionPolicyResolverTest : FunSpec({

    val trustedProfile = ExecutionTrustPolicy(
        profileName = "trusted_local",
        allowExecutableRuntime = true,
        docker = DockerExecutionTrustPolicy(),
    )

    val marketplaceProfile = ExecutionTrustPolicy(
        profileName = "marketplace_untrusted",
        allowExecutableRuntime = false,
        docker = DockerExecutionTrustPolicy(
            readOnlyRootFilesystem = true,
            user = "65532:65532",
            tmpFs = mapOf("/tmp" to "rw,noexec,nosuid,nodev,size=64m"),
        ),
    )

    val availableSupervisor = OpenShellConfig(supervisorPath = Paths.get("/bin/sh"))
    val missingSupervisor = OpenShellConfig(supervisorPath = null)

    fun validate(
        declared: ExecutionConfig?,
        policy: ExecutionPolicyConfig = ExecutionPolicyConfig(),
        source: AgentRegistrySourceIdentifier = AgentRegistrySourceIdentifier.Local,
        runtime: RuntimeId = RuntimeId.DOCKER,
        trust: ExecutionTrustPolicy = trustedProfile,
        openShellConfig: OpenShellConfig = availableSupervisor,
    ) = ExecutionPolicyResolver.validate(declared, policy, source, runtime, trust, openShellConfig)

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
            allowExecutableRuntime = false,
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
            allowExecutableRuntime = false,
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
            allowExecutableRuntime = false,
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
})
