package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.saket.bytesize.kibibytes
import me.saket.bytesize.mebibytes
import org.coralprotocol.coralserver.agent.execution.ExecutionConfig
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.defaultAsValue
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import java.nio.file.Path

/**
 * If this version of the server supports earlier versions of agent definitions, this field specifies the lowest.
 */
const val MINIMUM_SUPPORTED_AGENT_EDITION = 3

/**
 * The maximum (and current) supported agent edition.
 */
const val MAXIMUM_SUPPORTED_AGENT_VERSION = 4

@Serializable
data class RegistryAgent(
    private val info: RegistryAgentInfo,
    val edition: Int = MAXIMUM_SUPPORTED_AGENT_VERSION,
    val runtimes: LocalAgentRuntimes,
    val options: Map<String, AgentOption> = mapOf(),
    val llm: AgentLlmConfig? = null,
    val marketplace: RegistryAgentMarketplaceSettings? = null,
    val execution: ExecutionConfig? = null,

    @Transient
    val path: Path? = null,

    @Transient
    private val unresolvedExportSettings: Map<RuntimeId, UnresolvedAgentExportSettings> = mapOf(),
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

    val exportSettings: AgentExportSettingsMap = unresolvedExportSettings.mapValues { (runtime, settings) ->
        settings.resolve(runtime, this)
    }

    @Transient
    val defaultOptions = options
        .mapNotNull { (name, option) -> option.defaultAsValue()?.let { name to it } }
        .toMap()

    @Transient
    val requiredOptions = options
        .filterValues { it.required }
}

@Serializable
data class PublicRegistryAgent(
    val id: RegistryAgentIdentifier,
    val runtimes: List<RuntimeId>,
    val options: Map<String, AgentOption>,
    val exportSettings: PublicAgentExportSettingsMap,
    val execution: ExecutionConfig? = null,
)

fun RegistryAgent.toPublic(): PublicRegistryAgent = PublicRegistryAgent(
    id = identifier,
    runtimes = runtimes.toRuntimeIds(),
    options = options,
    exportSettings = exportSettings.mapValues { (_, settings) -> settings.toPublic() },
    execution = execution,
)