package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Optional
import io.github.smiley4.schemakenerator.core.annotations.Required
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

    /**
     * The default license here applies only to debug agents and tests.  The license field must be specified in real
     * agents.
     */
    @Required
    val license: RegistryAgentLicense = RegistryAgentLicense.Spdx("MIT"),

    @Optional
    val keywords: Set<String> = setOf(),

    @Optional
    val links: Map<String, String> = mapOf(),
)