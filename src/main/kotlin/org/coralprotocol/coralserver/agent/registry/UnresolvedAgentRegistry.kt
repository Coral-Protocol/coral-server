package org.coralprotocol.coralserver.agent.registry

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.coralprotocol.coralserver.agent.registry.reference.GitUnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.registry.reference.IndexUnresolvedRegistryAgent
import org.coralprotocol.coralserver.agent.registry.reference.LocalUnresolvedRegistryAgent
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * The serial names provided here are not pluralized because it makes the config file a little easier to understand when
 * using the "Array of Tables" syntax.  See https://toml.io/en/v1.0.0#array-of-tables
 */
@Serializable
data class UnresolvedAgentRegistry(
    @SerialName("local-agent")
    val localAgents: List<LocalUnresolvedRegistryAgent> = listOf(),

    @SerialName("git-agent")
    val gitAgents: List<GitUnresolvedRegistryAgent> = listOf(),

    @SerialName("indexed-agent")
    val indexedAgents: List<IndexUnresolvedRegistryAgent> = listOf(),

    @SerialName("inline-agent")
    val inlineAgents: List<UnresolvedInlineRegistryAgent> = listOf(),
) {
    fun resolve(context: RegistryResolutionContext): AgentRegistry {
        // Agents resolved from a local path require a reference to that path during the resolution process
        val agents = localAgents.flatMap {
            it.resolve(AgentResolutionContext(
                registryResolutionContext = context,
                path = context.tryRelative(Path.of(it.path))
            ))
        }.toMutableList()

        // Git/indexed/inline agents should resolve paths (e.g. exec runtime directories) using the current working
        // directory
        val defaultCtx = AgentResolutionContext(
            registryResolutionContext = context,
            path = Path.of(System.getProperty("user.dir"))
        )
        agents += gitAgents.flatMap { it.resolve(defaultCtx) }
        agents += indexedAgents.flatMap { it.resolve(defaultCtx) }
        agents += inlineAgents.flatMap { it.resolve(defaultCtx) }

        val duplicates = agents
            .groupingBy { it.info.identifier }
            .eachCount()
            .filter { (_, count) -> count > 1 }

        if (duplicates.isNotEmpty()) {
            throw RegistryException("Registry contains duplicate agents: ${duplicates.keys.joinToString(", ")}")
        }

        return AgentRegistry(agents)
    }
}