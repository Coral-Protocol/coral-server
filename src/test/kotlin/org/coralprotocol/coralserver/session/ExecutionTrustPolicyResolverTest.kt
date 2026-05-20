package org.coralprotocol.coralserver.session

import io.kotest.matchers.shouldBe
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.execution.resolveTrustPolicy
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.config.DockerConfig
import org.koin.test.inject

class ExecutionTrustPolicyResolverTest : CoralTest({
    test("testLocalTrustPolicyMirrorsTrustedTierConfig") {
        val dockerConfig by inject<DockerConfig>()

        val policy = AgentRegistrySourceIdentifier.Local.resolveTrustPolicy(dockerConfig)

        policy.profileName shouldBe "trusted_local"
        policy.docker shouldBe dockerConfig.trusted
    }

    test("testMarketplaceTrustPolicyMirrorsMarketplaceTierConfig") {
        val dockerConfig by inject<DockerConfig>()

        val policy = AgentRegistrySourceIdentifier.Marketplace.resolveTrustPolicy(dockerConfig)

        policy.profileName shouldBe "marketplace_untrusted"
        policy.docker shouldBe dockerConfig.marketplace
    }

    test("testLinkedTrustPolicyInheritsMarketplaceHardening") {
        val dockerConfig by inject<DockerConfig>()

        val linked = AgentRegistrySourceIdentifier.Linked("peer-server").resolveTrustPolicy(dockerConfig)
        val marketplace = AgentRegistrySourceIdentifier.Marketplace.resolveTrustPolicy(dockerConfig)

        linked shouldBe marketplace
    }

    test("testOperatorCanRequireMarketplaceDockerImageDigest") {
        val strict = DockerConfig(marketplace = DockerConfig().marketplace.copy(requireImageDigest = true))

        AgentRegistrySourceIdentifier.Marketplace.resolveTrustPolicy(strict)
            .docker.requireImageDigest shouldBe true
        AgentRegistrySourceIdentifier.Local.resolveTrustPolicy(strict)
            .docker.requireImageDigest shouldBe false
    }
})
