package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.Serializable

@Serializable
data class AgentExport(
    val agent: RegistryAgent,
    val quantity: UInt
)

@Serializable
data class PublicAgentExport(
    val agent: PublicRegistryAgent,
    val quantity: UInt
)

fun AgentExport.toPublic(id: String): PublicAgentExport {
    return PublicAgentExport(
        agent = agent.toPublic(id),
        quantity = quantity
    )
}