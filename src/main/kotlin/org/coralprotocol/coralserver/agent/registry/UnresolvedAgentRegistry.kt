package org.coralprotocol.coralserver.agent.registry

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.reference.UnresolvedRegistryAgentReference

private val logger = KotlinLogging.logger {}

@Serializable
class UnresolvedAgentRegistry() {
    @SerialName("agent-import")
    val importedAgents: Map<String, UnresolvedRegistryAgentReference> = HashMap()

    @SerialName("agent-export")
    val exportedAgents: Map<String, UnresolvedAgentExport> = HashMap()

    fun resolve(context: RegistryResolutionContext): AgentRegistry {
        val importedAgents = importedAgents.mapValues { (name, agent) ->
            logger.info { "Importing agent: $name" }
            agent.resolve(context, name)
        }

        return AgentRegistry(
            importedAgents,
            exportedAgents.mapValues { (name, unresolvedExport) ->
                logger.info { "Exporting agent: $name" }

                val agent = importedAgents[name] ?: throw RegistryException("Cannot export unknown agent: $name")
                unresolvedExport.resolve(name, agent)
            },
        )
    }
}