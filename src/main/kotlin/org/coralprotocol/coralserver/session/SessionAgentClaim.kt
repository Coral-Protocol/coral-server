@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.session

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.coralprotocol.coralserver.agent.payment.AgentBudgetUnit
import org.coralprotocol.coralserver.agent.registry.RegistryAgentClaimType
import org.coralprotocol.coralserver.util.InstantSerializer
import org.coralprotocol.coralserver.util.utcTimeNow
import kotlin.time.Instant

@Serializable
@JsonClassDiscriminator("type")
sealed class SessionAgentClaim {
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = utcTimeNow()

    abstract fun calculateCost(agent: SessionAgent): AgentBudgetUnit

    @Serializable
    @SerialName("rpc_claim")
    data class RpcClaim(
        val claimType: RegistryAgentClaimType,
        val quantity: UInt = 1u,
        val additionalDescription: String? = null
    ) : SessionAgentClaim() {
        override fun calculateCost(agent: SessionAgent): AgentBudgetUnit {
            val unitCost =
                agent.graphAgent.budgetSettings.claimTypeCosts[claimType.name] ?: claimType.cost
                ?: return AgentBudgetUnit.ZERO

            return unitCost * quantity
        }

        override fun toString(): String {
            return "${quantity}x claim of type ${claimType.name}, \"${claimType.description}\"${additionalDescription?.let { ", \"$it\"" } ?: ""}"
        }
    }

    @Serializable
    @SerialName("llm_proxy_claim")
    @Suppress("unused")
    data class LlmProxyClaim(
        val inputTokenCount: ULong,
        val inputTokenCost: AgentBudgetUnit,
        val outputTokenCount: ULong,
        val outputTokenCost: AgentBudgetUnit,
    ) : SessionAgentClaim() {
        override fun calculateCost(agent: SessionAgent): AgentBudgetUnit {
            return (outputTokenCost * inputTokenCount) + (outputTokenCost * outputTokenCount)
        }

        override fun toString(): String {
            return "llm proxy claim for $inputTokenCount input tokens ($inputTokenCost) and $outputTokenCount output tokens ($outputTokenCost)"
        }
    }
}