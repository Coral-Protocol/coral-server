package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class RegistryAgentInfo(
    @Description("The name of the agent, this should be as unique as possible")
    val name: String,

    @Description("The version of the agent, try to follow semantic versioning")
    val version: String,

    @Description("A full description of the agent, this description will be given to other agents to describe this agent's responsibilities, abilities and behaviours")
    val description: String? = null,

    @Description("A list of agent capabilities, for example the ability to refresh MCP resources")
    val capabilities: Set<AgentCapability> = setOf(),
) {
    val identifier: AgentRegistryIdentifier = AgentRegistryIdentifier(name, version)
}