package org.coralprotocol.coralserver.registry

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import org.coralprotocol.coralserver.agent.debug.EchoDebugAgent
import org.coralprotocol.coralserver.agent.debug.SeedDebugAgent
import org.coralprotocol.coralserver.agent.registry.PublicRestrictedRegistryAgent
import org.coralprotocol.coralserver.routes.api.v1.AgentRental
import org.coralprotocol.coralserver.session.sessionTest

class RentalApiTest : FunSpec({
    test("testRentalReserve") {
        // todo
    }

    test("testWallet") {
        sessionTest {
            ktor.client.get(AgentRental.Wallet()).shouldBeOK().body<String>()
                .shouldBeEqual(config.paymentConfig.remoteAgentWallet.shouldNotBeNull().walletAddress)
        }
    }

    test("testCatalog") {
        sessionTest({
            addLocalAgents(listOf(
                SeedDebugAgent(it.client).generate(),
                EchoDebugAgent(it.client).generate(export = true),
            ), "debug agents")
        }) {
            val catalog = ktor.client.get(AgentRental.Catalog())
                .shouldBeOK()
                .body<List<PublicRestrictedRegistryAgent>>()

            // seed agent should not be exported
            // echo debug agent should be exported
            catalog.shouldHaveSingleElement {
                it.registryAgent.id.name == "echo"
            }
        }
    }
})