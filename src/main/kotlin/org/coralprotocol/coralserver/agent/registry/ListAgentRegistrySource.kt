package org.coralprotocol.coralserver.agent.registry

import java.util.concurrent.ConcurrentHashMap

open class ListAgentRegistrySource(
    override val name: String,
    registryAgents: List<RegistryAgent> = listOf(),
    private val restrictions: Set<RegistryAgentRestriction> = setOf(),
) : AgentRegistrySource(AgentRegistrySourceIdentifier.Local) {
    private val agentCache: ConcurrentHashMap<RegistryAgentIdentifier, RegistryAgent> = ConcurrentHashMap()
    val registryAgents
        get() = agentCache.values

    init {
        addAllAgents(registryAgents)
    }

    fun addAllAgents(agents: List<RegistryAgent>) =
        agents.forEach { addAgent(it) }

    fun removeAllAgents(agents: List<RegistryAgent>) =
        agents.forEach { removeAgent(it) }

    fun clearAgents() =
        removeAllAgents(agentCache.values.toList())

    @Synchronized
    fun addAgent(agent: RegistryAgent) {
        if (agentCache.containsKey(agent.identifier))
            return

        agentCache[agent.identifier] = agent

        val catalogIndex = agents.indexOfFirst { it.name == agent.name }
        if (catalogIndex != -1) {
            agents[catalogIndex] = RegistryAgentCatalog(agent.name, agents[catalogIndex].versions + agent.version)
        } else {
            agents.add(RegistryAgentCatalog(agent.name, listOf(agent.version)))
        }
    }

    @Synchronized
    fun removeAgent(agent: RegistryAgent) {
        if (!agentCache.containsKey(agent.identifier))
            return

        agentCache.remove(agent.identifier)

        val catalogIndex = agents.indexOfFirst { it.name == agent.name }
        if (catalogIndex != -1) {
            val remainingVersions = agents[catalogIndex].versions.filterNot { it == agent.version }
            if (remainingVersions.isEmpty()) {
                agents.removeAt(catalogIndex)
            } else {
                agents[catalogIndex] = RegistryAgentCatalog(agent.name, remainingVersions)
            }
        }
    }

    override suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent {
        val agent = agentCache[agent]
            ?: throw RegistryException.AgentNotFoundException("Agent ${agent.name} not found in registry")

        return RestrictedRegistryAgent(agent, restrictions)
    }
}