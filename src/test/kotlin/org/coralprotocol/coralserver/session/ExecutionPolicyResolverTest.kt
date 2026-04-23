package org.coralprotocol.coralserver.session

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.coralprotocol.coralserver.agent.execution.ExecutionConfig
import org.coralprotocol.coralserver.agent.execution.ExecutionPolicyResolver
import org.coralprotocol.coralserver.agent.execution.ExecutionRejection
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustTier
import org.coralprotocol.coralserver.agent.execution.MinIsolation
import org.coralprotocol.coralserver.agent.execution.NetworkDeclaration
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.ExecutionPolicyConfig
import org.coralprotocol.coralserver.config.ExecutionTierPolicy

class ExecutionPolicyResolverTest : FunSpec({

    fun validate(
        declared: ExecutionConfig?,
        policy: ExecutionPolicyConfig = ExecutionPolicyConfig(),
        trust: ExecutionTrustTier = ExecutionTrustTier.TRUSTED,
        runtime: RuntimeId = RuntimeId.DOCKER,
    ) = ExecutionPolicyResolver.validate(declared, policy, trust, runtime)

    test("missingDeclarationSkipsValidation") {
        validate(declared = null).shouldBeEmpty()
    }

    test("declarationPassesThroughWhenPolicyIsPermissive") {
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            network = NetworkDeclaration(externalHosts = setOf("api.firecrawl.dev")),
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
        validate(declared, policy, ExecutionTrustTier.UNTRUSTED) shouldContainExactly listOf(
            ExecutionRejection.IsolationUnsupported(MinIsolation.CONTAINER, MinIsolation.PROCESS)
        )
    }

    test("hostsInDenylistAreRejected") {
        val policy = ExecutionPolicyConfig(
            marketplace = ExecutionTierPolicy(deniedHosts = setOf("evil.example.com"))
        )
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            network = NetworkDeclaration(externalHosts = setOf("api.firecrawl.dev", "evil.example.com")),
        )
        validate(declared, policy, ExecutionTrustTier.UNTRUSTED) shouldContainExactly listOf(
            ExecutionRejection.HostDenied("evil.example.com")
        )
    }

    test("hostsOutsideAllowlistAreRejected") {
        val policy = ExecutionPolicyConfig(
            marketplace = ExecutionTierPolicy(allowedHosts = setOf("api.firecrawl.dev"))
        )
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            network = NetworkDeclaration(externalHosts = setOf("api.firecrawl.dev", "other.example.com")),
        )
        validate(declared, policy, ExecutionTrustTier.UNTRUSTED) shouldContainExactly listOf(
            ExecutionRejection.HostDenied("other.example.com")
        )
    }

    test("multipleViolationsAreAllReported") {
        val policy = ExecutionPolicyConfig(
            marketplace = ExecutionTierPolicy(
                maxSupportedIsolation = MinIsolation.PROCESS,
                deniedHosts = setOf("bad.example.com"),
            )
        )
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            network = NetworkDeclaration(externalHosts = setOf("bad.example.com")),
        )
        validate(declared, policy, ExecutionTrustTier.UNTRUSTED, RuntimeId.EXECUTABLE) shouldBe listOf(
            ExecutionRejection.IsolationUnsupported(MinIsolation.CONTAINER, MinIsolation.PROCESS),
            ExecutionRejection.IsolationIncompatibleWithRuntime(MinIsolation.CONTAINER, RuntimeId.EXECUTABLE),
            ExecutionRejection.HostDenied("bad.example.com"),
        )
    }

    test("operatorPolicyAppliesPerTier") {
        val policy = ExecutionPolicyConfig(
            trusted = ExecutionTierPolicy(allowedHosts = null),
            marketplace = ExecutionTierPolicy(allowedHosts = setOf("api.firecrawl.dev")),
        )
        val declared = ExecutionConfig(
            minIsolation = MinIsolation.CONTAINER,
            network = NetworkDeclaration(externalHosts = setOf("other.example.com")),
        )
        validate(declared, policy, ExecutionTrustTier.TRUSTED).shouldBeEmpty()
        validate(declared, policy, ExecutionTrustTier.UNTRUSTED) shouldContainExactly listOf(
            ExecutionRejection.HostDenied("other.example.com")
        )
    }
})
