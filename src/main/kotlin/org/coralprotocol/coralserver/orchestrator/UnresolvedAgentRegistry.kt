package org.coralprotocol.coralserver.orchestrator

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class UnresolvedAgentRegistry() {
    @SerialName("agent-import")
    val importedAgents: Map<String, UnresolvedRegistryAgentReference> = HashMap()

    @SerialName("agent-export")
    val exportedAgents: Map<String, UnresolvedAgentExport> = HashMap()

    fun resolve(toml: Toml): AgentRegistry {
        val importedAgents = importedAgents.mapValues { (_, agent) ->
            agent.resolve(toml)
        }

        return AgentRegistry(
            importedAgents,
            exportedAgents.mapValues { (name, unresolvedExport) ->
                val agent = importedAgents[name] ?: throw RegistryException("Cannot export unknown agent: $name")
                unresolvedExport.resolve(name, agent)
            },
        )
    }
}