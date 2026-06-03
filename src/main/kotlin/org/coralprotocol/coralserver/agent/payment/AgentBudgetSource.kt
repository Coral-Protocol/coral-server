package org.coralprotocol.coralserver.agent.payment

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AgentBudgetSource {
    @SerialName("session")
    SESSION,

    @SerialName("agent")
    AGENT,
}