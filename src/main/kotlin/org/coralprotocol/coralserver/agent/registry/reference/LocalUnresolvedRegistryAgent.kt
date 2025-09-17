package org.coralprotocol.coralserver.agent.registry.reference

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.*
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * An agent referenced by a local file path.
 */
@Serializable
@SerialName("local")
data class LocalUnresolvedRegistryAgent(
    val path: String
) : UnresolvedRegistryAgent() {
    override fun resolve(context: AgentResolutionContext): List<RegistryAgent> {
        val agentTomlFile = context.tryRelative(Path.of(AGENT_FILE))
        try {
            return listOf(resolveRegistryAgentFromStream(
                file = agentTomlFile.toFile(),
                context = context.registryResolutionContext,
                exportSettings = unresolvedExportSettings
            ))
        }
        catch (e: Exception) {
            logger.error { "Failed to resolve local agent: ${agentTomlFile.toAbsolutePath()}" }
            throw e
        }
    }
}