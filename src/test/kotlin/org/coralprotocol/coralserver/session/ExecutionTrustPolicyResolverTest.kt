package org.coralprotocol.coralserver.session

import io.kotest.matchers.shouldBe
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustPolicyResolver
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustTier
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.config.DockerConfig
import org.coralprotocol.coralserver.config.SecurityConfig
import org.koin.test.inject

class ExecutionTrustPolicyResolverTest : CoralTest({
    test("testLocalTrustPolicyMirrorsTrustedTierConfig") {
        val resolver by inject<ExecutionTrustPolicyResolver>()
        val dockerConfig by inject<DockerConfig>()

        val policy = resolver.resolve(AgentRegistrySourceIdentifier.Local)
        val tier = dockerConfig.trusted

        policy.profileName shouldBe "trusted_local"
        policy.trustTier shouldBe ExecutionTrustTier.TRUSTED
        policy.allowExecutableRuntime shouldBe true
        policy.docker.requireImageDigest shouldBe false
        policy.docker.readOnlyRootFilesystem shouldBe tier.readOnlyRootFilesystem
        policy.docker.pidsLimit shouldBe tier.pidsLimit
        policy.docker.nanoCpus shouldBe tier.nanoCpus
        policy.docker.memoryLimitBytes shouldBe tier.memoryLimitBytes
        policy.docker.user shouldBe tier.user
        policy.docker.tmpFs shouldBe tier.tmpFs
    }

    test("testMarketplaceTrustPolicyMirrorsMarketplaceTierConfig") {
        val resolver by inject<ExecutionTrustPolicyResolver>()
        val dockerConfig by inject<DockerConfig>()

        val policy = resolver.resolve(AgentRegistrySourceIdentifier.Marketplace)
        val tier = dockerConfig.marketplace

        policy.profileName shouldBe "marketplace_untrusted"
        policy.trustTier shouldBe ExecutionTrustTier.UNTRUSTED
        policy.allowExecutableRuntime shouldBe false
        policy.docker.requireImageDigest shouldBe false
        policy.docker.readOnlyRootFilesystem shouldBe tier.readOnlyRootFilesystem
        policy.docker.pidsLimit shouldBe tier.pidsLimit
        policy.docker.nanoCpus shouldBe tier.nanoCpus
        policy.docker.memoryLimitBytes shouldBe tier.memoryLimitBytes
        policy.docker.user shouldBe tier.user
        policy.docker.tmpFs shouldBe tier.tmpFs
    }

    test("testLinkedTrustPolicyInheritsMarketplaceHardening") {
        val resolver by inject<ExecutionTrustPolicyResolver>()

        val linked = resolver.resolve(AgentRegistrySourceIdentifier.Linked("peer-server"))
        val marketplace = resolver.resolve(AgentRegistrySourceIdentifier.Marketplace)

        linked shouldBe marketplace
    }

    test("testOperatorCanUnblockMarketplaceExecutableRuntime") {
        val dockerConfig by inject<DockerConfig>()

        val permissiveResolver = ExecutionTrustPolicyResolver(
            securityConfig = SecurityConfig(allowMarketplaceExecutableRuntime = true),
            dockerConfig = dockerConfig,
        )

        permissiveResolver.resolve(AgentRegistrySourceIdentifier.Marketplace)
            .allowExecutableRuntime shouldBe true
    }

    test("testOperatorCanRequireMarketplaceDockerImageDigest") {
        val dockerConfig by inject<DockerConfig>()

        val strictResolver = ExecutionTrustPolicyResolver(
            securityConfig = SecurityConfig(requireMarketplaceDockerImageDigest = true),
            dockerConfig = dockerConfig,
        )

        strictResolver.resolve(AgentRegistrySourceIdentifier.Marketplace)
            .docker.requireImageDigest shouldBe true
        strictResolver.resolve(AgentRegistrySourceIdentifier.Local)
            .docker.requireImageDigest shouldBe false
    }
})
