package org.coralprotocol.coralserver.agent.registry

import UnresolvedAgentOption
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.runtime.AgentRuntime

@Serializable
data class UnresolvedRegistryAgent(
    val runtime: AgentRuntime,
    val options: Map<String, UnresolvedAgentOption>
) {
    fun resolve(): RegistryAgent = RegistryAgent(
        runtime = runtime,
        options = options.mapValues { (_, option) ->
            option.resolve()
        }
    )
}

@Serializable
data class RegistryAgent(
    val runtime: AgentRuntime,
    val options: Map<String, AgentOption>
)

@Serializable
data class PublicRegistryAgent(
    val id: String,
    val options: Map<String, AgentOption>
)

fun RegistryAgent.toPublic(id: String): PublicRegistryAgent = PublicRegistryAgent(
    id = id,
    options = options
)