package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.Serializable

@Serializable
@Description("A dependency that must be provided to use this agent. Dependencies are powered by options. If a dependency is only provided options that are not required, the dependency is effectively optional.")
data class RegistryAgentDependency(
    @Description("The unique name of this dependency")
    val name: String,

    @Description("A list of options associated with this dependency")
    val options: List<String>,
)
