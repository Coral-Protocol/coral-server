@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.payment

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.payment.JupiterService

const val MICRO_CORAL_TO_CORAL = 1_000_000.0

// Note that this is intentionally a sealed class because it gets optimized into an annoying type by OpenAPI generators
// as sealed interface
@Serializable
@JsonClassDiscriminator("type")
sealed class AgentClaimAmount {
    @Serializable
    @SerialName("usd")
    data class Usd(val amount: Double) : AgentClaimAmount()

    @Serializable
    @SerialName("coral")
    data class Coral(val amount: Double) : AgentClaimAmount()

    @Serializable
    @SerialName("micro_coral")
    data class MicroCoral(val amount: Long) : AgentClaimAmount()
}

suspend fun AgentClaimAmount.toCoral(jupiterService: JupiterService): Double = when (this) {
    is AgentClaimAmount.Coral -> amount / MICRO_CORAL_TO_CORAL
    is AgentClaimAmount.MicroCoral -> amount.toDouble()
    is AgentClaimAmount.Usd -> jupiterService.usdToCoral(amount)
}

suspend fun AgentClaimAmount.toUsd(jupiterService: JupiterService): Double = when (this) {
    is AgentClaimAmount.Coral -> jupiterService.coralToUsd(amount)
    is AgentClaimAmount.MicroCoral -> jupiterService.coralToUsd(amount / MICRO_CORAL_TO_CORAL)
    is AgentClaimAmount.Usd -> amount
}

suspend fun AgentClaimAmount.toMicroCoral(jupiterService: JupiterService): Long = when (this) {
    is AgentClaimAmount.Coral -> (amount * MICRO_CORAL_TO_CORAL).toLong()
    is AgentClaimAmount.MicroCoral -> amount
    is AgentClaimAmount.Usd -> (jupiterService.usdToCoral(amount) * MICRO_CORAL_TO_CORAL).toLong()
}