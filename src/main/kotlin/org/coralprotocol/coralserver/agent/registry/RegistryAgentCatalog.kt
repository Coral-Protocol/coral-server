package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
@Description("A catalog describing an agent and it's available versions")
data class RegistryAgentCatalog(
    @Description("The agent's registry name")
    val name: String,

    @Description("All versions this agent is available in")
    val versions: List<String>
)
