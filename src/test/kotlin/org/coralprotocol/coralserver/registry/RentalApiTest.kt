package org.coralprotocol.coralserver.registry

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.agent.payment.AgentClaimAmount
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.agent.runtime.FunctionRuntime
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.PaymentConfig
import org.coralprotocol.coralserver.routes.api.v1.AgentRental
import org.coralprotocol.coralserver.utils.dsl.registryAgent
import org.koin.test.inject

class RentalApiTest : CoralTest({
    test("testRentalReserve") {
        // todo
    }

    test("testWallet") {
        val client by inject<HttpClient>()
        val config by inject<PaymentConfig>()

        client.get(AgentRental.Wallet()).shouldBeOK().body<String>()
            .shouldBeEqual(config.remoteAgentWallet.shouldNotBeNull().walletAddress)
    }

    test("testCatalog") {
        val registry by inject<AgentRegistry>()

        // add a single exported agent to the registry, the debug agents are not exported by default and to test that
        // only explicitly exported agents show as exported, an additional exported agent should be added to a list of
        // non-exported agents
        registry.sources.add(
            ListAgentRegistrySource(
                "test",
                listOf(registryAgent("exported") {
                    runtime(FunctionRuntime())
                    exportSetting(
                        RuntimeId.FUNCTION, UnresolvedAgentExportSettings(
                            quantity = 1u,
                            pricing = RegistryAgentExportPricing(
                                minPrice = AgentClaimAmount.Usd(1.0),
                                maxPrice = AgentClaimAmount.Usd(10.0)
                            ),
                            options = mapOf()
                        )
                    )
                })
            )
        )

        val client by inject<HttpClient>()
        val catalog = client.get(AgentRental.Catalog())
            .shouldBeOK()
            .body<List<PublicRestrictedRegistryAgent>>()

        // seed agent should not be exported
        // echo debug agent should be exported
        catalog.shouldHaveSingleElement {
            it.registryAgent.id.name == "exported"
        }
    }
})