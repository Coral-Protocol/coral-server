package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
data class AgentRegistryIdentifier(
    @Description("The exact name of the agent in the registry")
    val name: String,

    @Description("The exact version of the agent in the registry")
    val version: String,
) {
    override fun toString(): String {
        return "$name:$version"
    }

    fun toInfo(): RegistryAgentInfo = RegistryAgentInfo(name, version)
}