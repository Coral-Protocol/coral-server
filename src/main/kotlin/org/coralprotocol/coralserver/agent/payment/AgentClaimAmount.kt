@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.payment

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

// Note that this is intentionally a sealed class because it gets optimized into a annoying type by OpenAPI generators
// as sealed interface
@Serializable
@JsonClassDiscriminator("type")
sealed class AgentClaimAmount {
    @Serializable
    @SerialName("usd_cents")
    data class UsdCents(val amount: Long) : AgentClaimAmount()

    @Serializable
    @SerialName("coral")
    data class Coral(val amount: Long) : AgentClaimAmount()
}

fun AgentClaimAmount.toCoral(): Long = when (this) {
    is AgentClaimAmount.Coral -> amount
    is AgentClaimAmount.UsdCents -> {
        // todo: use api
        val exchange = 100000L
        amount / exchange
    }
}