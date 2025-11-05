package org.coralprotocol.coralserver.x402

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.json.JsonObject

data class X402PaymentPayload(
    @Description("Version of the x402 payment protocol")
    val x402Version: Int,

    @Description("scheme is the scheme value of the accepted `paymentRequirements` the client is using to pay")
    val scheme: String,

    @Description("network is the network id of the accepted `paymentRequirements` the client is using to pay")
    val network: String,

    @Description("payload is scheme dependent")
    val payload: JsonObject? = null
)