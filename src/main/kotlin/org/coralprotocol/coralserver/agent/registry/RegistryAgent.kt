package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.agent.execution.ExecutionConfig
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.AgentOptionSerializerMap
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import java.nio.file.Path

/**
 * If this version of the server supports earlier versions of agent definitions, this field specifies the lowest.
 */
const val MINIMUM_SUPPORTED_AGENT_EDITION = 3

/**
 * The maximum (and current) supported agent edition.
 */
const val MAXIMUM_SUPPORTED_AGENT_VERSION = 5

@Serializable
data class RegistryAgent(
    private val info: RegistryAgentInfo,
    val edition: Int = MAXIMUM_SUPPORTED_AGENT_VERSION,
    val runtimes: LocalAgentRuntimes,

    @Serializable(with = AgentOptionSerializerMap::class)
    val options: Map<String, AgentOption> = mapOf(),
    val llm: AgentLlmConfig? = null,
    val marketplace: RegistryAgentMarketplaceSettings? = null,
    val dependencies: List<RegistryAgentDependency> = listOf(),
    val claimTypes: List<RegistryAgentClaimType> = listOf(),
    val execution: ExecutionConfig? = null,

    @Transient
    val path: Path? = null,
) {
    @Transient
    val description = info.description

    @Transient
    val identifier = info.identifier

    @Transient
    val name = identifier.name

    @Transient
    val version = identifier.version

    @Transient
    val capabilities = info.capabilities

    @Transient
    val readme = info.readme

    @Transient
    val summary = info.summary

    @Transient
    val license = info.license

    @Transient
    val keywords = info.keywords

    @Transient
    val links = info.links

    @Transient
    val llmProxies = llm?.proxies ?: listOf()

    @Transient
    val dependencyMap = dependencies.associateBy { it.name }

    @Transient
    val claimTypeMap = claimTypes.associateBy { it.name }

    @Transient
    val defaultOptions = options
        .mapNotNull { (name, option) -> option.withDefaultValue()?.let { name to it } }
        .toMap()

    @Transient
    val requiredOptions = options
        .filterValues { it.required }
}
