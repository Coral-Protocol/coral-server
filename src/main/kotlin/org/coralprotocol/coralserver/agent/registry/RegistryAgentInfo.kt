package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.Serializable

@Serializable
data class RegistryAgentInfo(
    val description: String?,
    val capabilities: Set<AgentCapability>,
    val identifier: RegistryAgentIdentifier,

    @Optional
    val readme: String? = null,

    @Optional
    val summary: String? = null,

    @Optional
    val license: String? = null,

    @Optional
    val links: Map<String, String> = mapOf(),
)