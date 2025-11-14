package org.coralprotocol.coralserver.x402

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class X402PaymentRequired(
    @Description("Version of the x402 payment protocol")
    val x402Version: Int,

    @Description("List of payment requirements that the resource server accepts. A resource server may accept on multiple chains, or in multiple currencies.")
    val accepts: List<X402PaymentRequirement>,

    @Description("Message from the resource server to the client to communicate errors in processing payment")
    val error: String
)