package org.coralprotocol.coralserver.agent.registry.reference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.*
import org.coralprotocol.coralserver.logging.LoggingInterface
import org.koin.core.component.inject
import java.nio.file.Path

/**
 * An agent referenced by a local file path.
 */
@Serializable
@SerialName("local")
data class LocalUnresolvedRegistryAgent(
    val path: String
) : UnresolvedRegistryAgent() {
    private val logger by inject<LoggingInterface>()

    override fun resolve(context: AgentResolutionContext): List<RegistryAgent> {
        val agentTomlFile = context.tryRelative(Path.of(AGENT_FILE))
        try {
            return listOf(
                resolveRegistryAgentFromStream(
                    file = agentTomlFile.toFile(),
                    context = context.registryResolutionContext,
                    exportSettings = unresolvedExportSettings
                )
            )
        } catch (e: Exception) {
            logger.error { "Failed to resolve local agent: ${agentTomlFile.toAbsolutePath()}" }
            throw e
        }
    }
}