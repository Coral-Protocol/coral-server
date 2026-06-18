@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.payment

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.AGENT_FILE

const val BUDGET_CLAIM_ADDITIONAL_DESCRIPTION_MAX_LENGTH = 256

@Serializable
data class AgentClaimRequest(
    @Description("The name of the claim type. This claim type must be defined in the agent's $AGENT_FILE file")
    val claimTypeName: String,

    @Description("An additional description of the claim, clamped at $BUDGET_CLAIM_ADDITIONAL_DESCRIPTION_MAX_LENGTH characters long")
    val additionalDescription: String,

    @Description("Set this to false if the agent wants to handle budget exhaustion itself. Budget settings may kill agents on budget exhaustion even if this is set to false")
    val autoKill: Boolean = true
)