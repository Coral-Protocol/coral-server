package org.coralprotocol.coralserver.session

import io.kotest.matchers.shouldBe
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustTier
import org.coralprotocol.coralserver.agent.execution.resolveTrustPolicy
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.config.DockerConfig
import org.coralprotocol.coralserver.config.SecurityConfig
import org.koin.test.inject

class ExecutionTrustPolicyResolverTest : CoralTest({
    test("testLocalTrustPolicyMirrorsTrustedTierConfig") {
        val dockerConfig by inject<DockerConfig>()
        val securityConfig by inject<SecurityConfig>()

        val policy = AgentRegistrySourceIdentifier.Local.resolveTrustPolicy(dockerConfig, securityConfig)

        policy.profileName shouldBe "trusted_local"
        policy.trustTier shouldBe ExecutionTrustTier.TRUSTED
        policy.allowExecutableRuntime shouldBe true
        policy.docker shouldBe dockerConfig.trusted
    }

    test("testMarketplaceTrustPolicyMirrorsMarketplaceTierConfig") {
        val dockerConfig by inject<DockerConfig>()
        val securityConfig by inject<SecurityConfig>()

        val policy = AgentRegistrySourceIdentifier.Marketplace.resolveTrustPolicy(dockerConfig, securityConfig)

        policy.profileName shouldBe "marketplace_untrusted"
        policy.trustTier shouldBe ExecutionTrustTier.UNTRUSTED
        policy.allowExecutableRuntime shouldBe false
        policy.docker shouldBe dockerConfig.marketplace
    }

    test("testLinkedTrustPolicyInheritsMarketplaceHardening") {
        val dockerConfig by inject<DockerConfig>()
        val securityConfig by inject<SecurityConfig>()

        val linked = AgentRegistrySourceIdentifier.Linked("peer-server")
            .resolveTrustPolicy(dockerConfig, securityConfig)
        val marketplace = AgentRegistrySourceIdentifier.Marketplace
            .resolveTrustPolicy(dockerConfig, securityConfig)

        linked shouldBe marketplace
    }

    test("testOperatorCanUnblockUntrustedExecutableRuntime") {
        val dockerConfig by inject<DockerConfig>()
        val permissive = SecurityConfig(allowUntrustedExecutableRuntime = true)

        AgentRegistrySourceIdentifier.Marketplace.resolveTrustPolicy(dockerConfig, permissive)
            .allowExecutableRuntime shouldBe true
    }

    test("testOperatorCanRequireMarketplaceDockerImageDigest") {
        val securityConfig by inject<SecurityConfig>()
        val strict = DockerConfig(marketplace = DockerConfig().marketplace.copy(requireImageDigest = true))

        AgentRegistrySourceIdentifier.Marketplace.resolveTrustPolicy(strict, securityConfig)
            .docker.requireImageDigest shouldBe true
        AgentRegistrySourceIdentifier.Local.resolveTrustPolicy(strict, securityConfig)
            .docker.requireImageDigest shouldBe false
    }
})
