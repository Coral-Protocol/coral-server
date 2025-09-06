package org.coralprotocol.coralserver.agent.registry

class AgentRegistry(
    val agents: List<RegistryAgent> = listOf()
) {
    fun findAgent(id: AgentRegistryIdentifier) = agents.find { it.id == id }

    companion object
}