@file:OptIn(ExperimentalSerializationApi::class)

package org.coralprotocol.coralserver.agent.registry

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.decodeFromNativeReader
import org.coralprotocol.coralserver.agent.runtime.LocalAgentRuntimes
import org.coralprotocol.coralserver.agent.runtime.RuntimeId
import org.coralprotocol.coralserver.config.SecurityConfig
import org.coralprotocol.coralserver.routes.api.v1.filterNotNullValues
import java.io.File
import java.nio.file.Path

private val logger = KotlinLogging.logger {  }

class RegistryAgent(
    val info: RegistryAgentInfo,
    val runtimes: LocalAgentRuntimes,
    val options: Map<String, AgentOption>,
    val path: Path,
    unresolvedExportSettings: UnresolvedAgentExportSettingsMap
) {
    val exportSettings: AgentExportSettingsMap = unresolvedExportSettings.mapValues { (runtime, settings) ->
        settings.resolve(runtime, this)
    }

    val defaultOptions = options
        .mapValues { (name, option) -> option.defaultAsValue() }
        .filterNotNullValues()

    val requiredOptions = options
        .filterValues { it.required }
}

@Serializable
data class PublicRegistryAgent(
    val id: AgentRegistryIdentifier,
    val runtimes: List<RuntimeId>,
    val options: Map<String, AgentOption>,
    val exportSettings: PublicAgentExportSettingsMap
)

fun RegistryAgent.toPublic(): PublicRegistryAgent = PublicRegistryAgent(
    id = info.identifier,
    runtimes = runtimes.toRuntimeIds(),
    options = options,
    exportSettings = exportSettings.mapValues { (_, settings) -> settings.toPublic() }
)

/**
 * This function deserializes an [UnresolvedInlineRegistryAgent] from the provided [stream], then resolves it using the
 * provided [context].
 *
 * Important note! [UnresolvedInlineRegistryAgent] contains the [UnresolvedInlineRegistryAgent.agentExportSettings] field,
 * which poses a security risk.  Users that import definitions via reference (indexed, git, path, etc.) might not want
 * export settings in that file to take effect.
 *
 * If [SecurityConfig.enableReferencedExporting] is set to true and [exportSettings] is null then the provided export
 * settings will be used.
 */
fun resolveRegistryAgentFromStream(
    file: File,
    context: RegistryResolutionContext,
    exportSettings: UnresolvedAgentExportSettingsMap
): RegistryAgent {
    val unresolved = context.serializer.decodeFromNativeReader<UnresolvedInlineRegistryAgent>(file.reader())
    if (!context.config.security.enableReferencedExporting) {
        if (unresolved.unresolvedExportSettings.isNotEmpty()) {
            logger.warn { "Referenced agent file $file contains export settings, but [security.enableReferencedExporting] is false. Export settings in this file will be ignored" }
        }

        unresolved.unresolvedExportSettings = exportSettings
    }
    else {
        unresolved.unresolvedExportSettings += exportSettings
    }

    return unresolved.resolve(AgentResolutionContext(
        registryResolutionContext = context,
        path = file.toPath().parent
    )).first()
}