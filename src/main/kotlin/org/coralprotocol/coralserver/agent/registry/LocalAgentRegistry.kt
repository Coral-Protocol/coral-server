package org.coralprotocol.coralserver.agent.registry

class LocalAgentRegistry(val agents: List<RegistryAgent> = listOf()): AgentRegistry {
    override fun listAgents(): List<RegistryAgent> = agents
    override fun findAgent(id: AgentRegistryIdentifier) = agents.find { it.info.identifier == id }

    companion object
}