package org.coralprotocol.coralserver.agent.registry.reference

import com.akuleshov7.ktoml.source.decodeFromStream
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.RegistryAgent
import org.coralprotocol.coralserver.agent.registry.RegistryResolutionContext
import org.coralprotocol.coralserver.agent.registry.UnresolvedRegistryAgent
import java.nio.file.Path
import kotlin.io.path.inputStream

private val logger = KotlinLogging.logger {}

/**
 * An agent referenced by a local file path.
 */
@Serializable
data class LocalReference(
    val path: String,
) : UnresolvedRegistryAgentReference {
    override fun resolve(
        context: RegistryResolutionContext,
        name: String
    ): RegistryAgent {
        try {
            val agentTomlFile = Path.of(path, AGENT_FILE)
            val agent = context.serializer.decodeFromStream<UnresolvedRegistryAgent>(agentTomlFile.inputStream())
            return agent.resolve()
        }
        catch (e: Exception) {
            logger.error { "Failed to resolve local agent: $path" }
            throw e
        }
    }
}