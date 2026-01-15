package org.coralprotocol.coralserver.agent.registry

class MarketplaceAgentRegistrySource : AgentRegistrySource(AgentRegistrySourceIdentifier.Marketplace) {
    override val agents: MutableList<RegistryAgentCatalog>
        get() = TODO("Not yet implemented")

    override suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent {
        TODO("Not yet implemented")
    }
}