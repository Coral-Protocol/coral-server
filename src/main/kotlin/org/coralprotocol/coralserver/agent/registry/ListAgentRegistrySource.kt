package org.coralprotocol.coralserver.agent.registry

/**
 * A registry agent source that gets all of its agents from a list of already resolved known agents.  This is used for
 * local sources.
 */
class ListAgentRegistrySource(
    identifier: AgentRegistrySourceIdentifier,
    private val registryAgents: List<RegistryAgent>,
    private val restrictions: Set<RegistryAgentRestriction> = setOf()
) : AgentRegistrySource(identifier) {

    override val agents: List<RegistryAgentCatalog> = buildList {
        buildMap {
            registryAgents.forEach {
                getOrPut(it.info.identifier.name, ::mutableListOf).add(it.info.identifier.version)
            }
        }.forEach { (name, versions) ->
            add(RegistryAgentCatalog(name, versions))
        }
    }

    private val agentMap = registryAgents.associateBy { it.info.identifier }

    override suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent {
        val agent = agentMap[agent]
            ?: throw RegistryException.AgentNotFoundException("Agent ${agent.name} not found in registry")

        return RestrictedRegistryAgent(agent, restrictions)
    }
}