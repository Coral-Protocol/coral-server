package org.coralprotocol.coralserver.registry

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldNotBeEmpty
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId

class LocalRegistryTest : CoralTest({
    val testAgentName = "test"
    val testAgentVersion = "1.0.0"

    val testExportedAgentName = "export test"
    val testExportAgentVersion = "1.1.0"

    val testAgent = RegistryAgent(
        info = RegistryAgentInfo(
            description = "test agent",
            capabilities = setOf(),
            identifier = RegistryAgentIdentifier(testAgentName, testAgentVersion, AgentRegistrySourceIdentifier.Local)
        ),
        runtimes = LocalAgentRuntimes(),
    )

    val testExportedAgent = RegistryAgent(
        info = RegistryAgentInfo(
            description = "test exported agent",
            capabilities = setOf(),
            identifier = RegistryAgentIdentifier(
                testExportedAgentName,
                testExportAgentVersion,
                AgentRegistrySourceIdentifier.Local
            )
        ),
        runtimes = LocalAgentRuntimes(
            functionRuntime = FunctionRuntime { _, _ ->
                // needed for export settings
            }
        ),
        unresolvedExportSettings = mapOf(
            RuntimeId.FUNCTION to UnresolvedAgentExportSettings(
                quantity = 1u,
                pricing = RegistryAgentExportPricing(
                    minPrice = AgentClaimAmount.Usd(1.0),
                    maxPrice = AgentClaimAmount.Usd(10.0)
                ),
                options = mapOf()
            )
        )
    )

    class MockMarketplaceSource : AgentRegistrySource(AgentRegistrySourceIdentifier.Marketplace) {
        override val agents: MutableList<RegistryAgentCatalog> =
            mutableListOf(RegistryAgentCatalog(testAgentName, listOf(testAgentVersion)))

        override suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent {
            if (agent.name != testAgentName) throw Exception("Agent not found")
            return RestrictedRegistryAgent(testAgent, setOf(RegistryAgentRestriction.RemoteOnly))
        }
    }

    test("testDuplicates") {
        val registry = AgentRegistry {
            // should not contribute to resolution, should contribute to count
            addSource(MockMarketplaceSource())

            // same agent added twice from different sources
            addLocalAgents("test agent batch 1", listOf(testAgent))
            addLocalAgents("test agent batch 2", listOf(testAgent))
        }

        shouldNotThrowAny {
            val agent =
                registry.resolveAgent(
                    RegistryAgentIdentifier(
                        testAgentName,
                        testAgentVersion,
                        AgentRegistrySourceIdentifier.Local
                    )
                )
                    .registryAgent

            agent.name.shouldBeEqual(testAgentName)
            agent.version.shouldBeEqual(testAgentVersion)
        }

        // 1st is marketplace, 2nd is local, and there should be no third because it was removed from deduplication
        registry.agents.shouldHaveSize(2)
    }

    test("testExport") {
        val registry = AgentRegistry {
            addLocalAgents("test agent batch 1", listOf(testAgent, testExportedAgent))
        }

        val testAgent = shouldNotThrowAny {
            registry.resolveAgent(
                RegistryAgentIdentifier(
                    testAgentName,
                    testAgentVersion,
                    AgentRegistrySourceIdentifier.Local
                )
            )
        }

        val testExportedAgent = shouldNotThrowAny {
            registry.resolveAgent(
                RegistryAgentIdentifier(
                    testExportedAgentName,
                    testExportAgentVersion,
                    AgentRegistrySourceIdentifier.Local
                )
            )
        }

        testAgent.registryAgent.exportSettings.shouldBeEmpty()
        testExportedAgent.registryAgent.exportSettings.shouldNotBeEmpty()
        registry.getExportedAgents().shouldHaveSingleElement {
            it.registryAgent.name == testExportedAgentName &&
                    it.registryAgent.version == testExportAgentVersion
        }
    }
})