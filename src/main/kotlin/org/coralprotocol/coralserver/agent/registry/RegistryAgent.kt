@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.coralprotocol.coralserver.agent.registry.option.AgentOption
import org.coralprotocol.coralserver.agent.registry.option.defaultAsValue
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import java.nio.file.Path

const val FIRST_AGENT_EDITION = 1
const val CURRENT_AGENT_EDITION = 3

@Serializable
data class RegistryAgent(
    private val info: RegistryAgentInfo,
    val edition: Int = CURRENT_AGENT_EDITION,
    val runtimes: LocalAgentRuntimes,
    val options: Map<String, AgentOption> = mapOf(),
    val marketplace: RegistryAgentMarketplaceSettings? = null,

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
    val exportSettings: PublicAgentExportSettingsMap
)

fun RegistryAgent.toPublic(): PublicRegistryAgent = PublicRegistryAgent(
    id = identifier,
    runtimes = runtimes.toRuntimeIds(),
    options = options,
    exportSettings = exportSettings.mapValues { (_, settings) -> settings.toPublic() }
)