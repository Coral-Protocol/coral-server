package org.coralprotocol.coralserver.orchestrator

import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.orchestrator.runtime.RuntimeId

@Serializable
data class AgentExport(
    val agent: RegistryAgent,
    val runtimes: List<RuntimeId>,
    val quantity: UInt
)

@Serializable
data class PublicAgentExport(
    val agent: PublicRegistryAgent,
    val runtimes: List<RuntimeId>,
    val quantity: UInt
)

fun AgentExport.toPublic(id: String): PublicAgentExport {
    return PublicAgentExport(
        agent = agent.toPublic(id),
        runtimes = runtimes,
        quantity = quantity
    )
}