package org.coralprotocol.coralserver.registry

import io.kotest.assertions.ktor.client.shouldBeOK
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import org.coralprotocol.coralserver.CoralTest
import org.coralprotocol.coralserver.config.PaymentConfig
import org.coralprotocol.coralserver.routes.api.v1.AgentRental
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
})