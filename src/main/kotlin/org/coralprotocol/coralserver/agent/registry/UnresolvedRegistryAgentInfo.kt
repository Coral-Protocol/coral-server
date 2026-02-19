package org.coralprotocol.coralserver.agent.registry

import io.github.smiley4.schemakenerator.core.annotations.Description
import io.github.smiley4.schemakenerator.core.annotations.Optional
import kotlinx.serialization.Serializable

@Serializable
data class UnresolvedRegistryAgentInfo(
    @Description("The name of the agent, this should be as unique as possible")
    val name: String,

    @Description("The version of the agent, try to follow semantic versioning")
    val version: String,

    @Description("A full description of the agent, this description will be given to other agents to describe this agent's responsibilities, abilities and behaviours")
    @Optional
    val description: String? = null,

    @Description("A list of agent capabilities, for example the ability to refresh MCP resources")
    val capabilities: Set<AgentCapability> = setOf(),

    @Description("A markdown readme for this agent")
    val readme: String,

    @Description("A short markdown summary for this agent")
    val summary: String,

    @Optional
    @Description("The license name as a SDPX expression or the full license text for this agent")
    val license: RegistryAgentLicense,

    @Optional
    @Description("A list of keywords for this agent.  These keywords help users search for agents")
    val keywords: Set<String> = setOf(),

    @Optional
    @Description("Links to other resources related to this agent, e.g source repository")
    val links: Map<String, String> = mapOf(),
) {
    fun resolve(registrySourceIdentifier: AgentRegistrySourceIdentifier): RegistryAgentInfo =
        RegistryAgentInfo(
            description = description,
            capabilities = capabilities,
            identifier = RegistryAgentIdentifier(name, version, registrySourceIdentifier),
            readme = readme,
            summary = summary,
            license = license,
            keywords = keywords,
            links = links
        )
}