package org.coralprotocol.coralserver.agent.registry

interface AgentRegistry {
    fun listAgents(): List<RegistryAgent>
    fun findAgent(id: AgentRegistryIdentifier): RegistryAgent?
}