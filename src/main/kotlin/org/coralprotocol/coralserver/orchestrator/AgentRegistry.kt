package org.coralprotocol.coralserver.orchestrator

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias AgentRegistry = HashMap<String, RegistryAgent>

@Serializable
class UnresolvedAgentRegistry() {
    @SerialName("registry")
    val agents: Map<String, UnresolvedRegistryAgentReference> = HashMap()

    fun resolve(toml: Toml): AgentRegistry {
        return agents.mapValues { (_, agent) ->
            agent.resolve(toml)
        } as AgentRegistry
    }
}