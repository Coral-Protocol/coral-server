package org.coralprotocol.coralserver.agent.registry

/**
 * A registry agent source that gets all of its agents from a list of already resolved known agents.  This is used for
 * local sources.
 */
class ListAgentRegistrySource(
    val registryAgents: List<RegistryAgent> = listOf(),
    private val restrictions: Set<RegistryAgentRestriction> = setOf()
) : AgentRegistrySource(AgentRegistrySourceIdentifier.Local) {
    override val agents: List<RegistryAgentCatalog> = buildList {
        buildMap {
            registryAgents.forEach {
                getOrPut(it.name, ::mutableListOf).add(it.version)
            }
        }.forEach { (name, versions) ->
            add(RegistryAgentCatalog(name, versions))
        }
    }

    private val agentMap = registryAgents.associateBy { it.identifier }

    override suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent {
        val agent = agentMap[agent]
            ?: throw RegistryException.AgentNotFoundException("Agent ${agent.name} not found in registry")

        return RestrictedRegistryAgent(agent, restrictions)
    }
}