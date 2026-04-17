package org.coralprotocol.coralserver.session

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancel
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustPolicyResolver
import org.coralprotocol.coralserver.agent.execution.ExecutionTrustTier
import org.coralprotocol.coralserver.agent.graph.AgentGraph
import org.coralprotocol.coralserver.agent.graph.GraphAgentProvider
import org.coralprotocol.coralserver.agent.registry.AgentRegistrySourceIdentifier
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.utils.dsl.graphAgentPair
import org.koin.test.inject

class ExecutionTrustPolicyResolverTest : CoralTest({
    test("testMarketplaceTrustPolicyDefaults") {
        val resolver by inject<ExecutionTrustPolicyResolver>()

        val policy = resolver.resolve(AgentRegistrySourceIdentifier.Marketplace)

        policy.profileName shouldBe "marketplace_untrusted"
        policy.trustTier shouldBe ExecutionTrustTier.UNTRUSTED
        policy.allowExecutableRuntime shouldBe false
        policy.docker.requireImageDigest shouldBe false
        policy.docker.readOnlyRootFilesystem shouldBe true
        policy.docker.pidsLimit shouldBe 256L
        policy.docker.nanoCpus shouldBe 1_000_000_000L
        policy.docker.memoryLimitBytes shouldBe 512L * 1024L * 1024L
        policy.docker.user shouldBe "65532:65532"
    }

    test("testLocalTrustPolicyDefaults") {
        val resolver by inject<ExecutionTrustPolicyResolver>()

        val policy = resolver.resolve(AgentRegistrySourceIdentifier.Local)

        policy.profileName shouldBe "trusted_local"
        policy.trustTier shouldBe ExecutionTrustTier.TRUSTED
        policy.allowExecutableRuntime shouldBe true
        policy.docker.requireImageDigest shouldBe false
        policy.docker.pidsLimit shouldBe 256L
        policy.docker.nanoCpus shouldBe null
        policy.docker.memoryLimitBytes shouldBe null
    }

    test("testSessionStateIncludesResolvedTrustPolicy") {
        val localSessionManager by inject<LocalSessionManager>()

        val (session, _) = localSessionManager.createSession(
            "test", AgentGraph(
                agents = mapOf(
                    graphAgentPair("marketplace") {
                        registryAgent {
                            registrySourceId = AgentRegistrySourceIdentifier.Marketplace
                            runtime(FunctionRuntime())
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    },
                    graphAgentPair("local") {
                        registryAgent {
                            registrySourceId = AgentRegistrySourceIdentifier.Local
                            runtime(FunctionRuntime())
                        }
                        provider = GraphAgentProvider.Local(RuntimeId.FUNCTION)
                    }
                )
            )
        )

        val states = session.getState().agents.associateBy { it.name }

        states.getValue("marketplace").executionProfile shouldBe "marketplace_untrusted"
        states.getValue("marketplace").trustTier shouldBe ExecutionTrustTier.UNTRUSTED
        states.getValue("local").executionProfile shouldBe "trusted_local"
        states.getValue("local").trustTier shouldBe ExecutionTrustTier.TRUSTED

        session.sessionScope.cancel()
    }
})
