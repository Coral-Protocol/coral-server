package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class RegistryAgentIdentifier(
    @Description("The exact name of the agent in the registry")
    val name: String,

    @Description("The exact version of the agent in the registry")
    val version: String,

    @Description("The identifier for the registry source that contains this agent")
    val registrySourceId: AgentRegistrySourceIdentifier,
) {
    override fun toString(): String {
        return "$registrySourceId/$name:$version"
    }
}