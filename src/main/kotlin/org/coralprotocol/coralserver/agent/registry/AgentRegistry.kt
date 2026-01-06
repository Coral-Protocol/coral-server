package org.coralprotocol.coralserver.agent.registry

import kotlinx.serialization.SerializationException
import net.peanuuutz.tomlkt.decodeFromNativeReader
import org.coralprotocol.coralserver.config.toml
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.io.InputStream
import java.nio.file.Path

class AgentRegistrySourceBuilder : KoinComponent {
    val logger by inject<Logger>(named(LOGGER_CONFIG))
    val sources = mutableListOf<AgentRegistrySource>()

    fun addSource(source: AgentRegistrySource) {
        sources.add(source)
    }

    fun addMarketplace() {
        sources.add(MarketplaceAgentRegistrySource())
    }

    fun addLocal(path: Path) {
        addFromStream(path.toFile().inputStream(), path)
    }

    fun addLocalAgents(agents: List<RegistryAgent>, identifier: String) {
        // agent lookups on local registry sources are performed on all local registries, so it is important that
        // no duplicates exist between them
        val duplicates = agents
            .filter {
                sources
                    .filter { it.identifier == AgentRegistrySourceIdentifier.Local }
                    .any {
                        it.agents.any { catalog ->
                            catalog.versions.any { version ->
                                agents.any { newAgent ->
                                    newAgent.name == catalog.name && newAgent.version == version
                                }
                            }
                        }
                    }
            }

        val agents = if (duplicates.isNotEmpty()) {
            logger.warn {
                "Registry '$identifier' contains ${duplicates.size} duplicate agent(s): ${
                    duplicates.joinToString(
                        ", "
                    ) { it.identifier.toString() }
                }"
            }
            logger.warn { "These duplicates will be ignored from this registry source" }

            agents.filter { !duplicates.contains(it) }
        } else {
            agents
        }

        if (agents.isEmpty())
            logger.warn { "Registry '$identifier' contains no agents" }

        sources.add(ListAgentRegistrySource(agents))
    }

    fun addFromStream(stream: InputStream, path: Path = Path.of(System.getProperty("user.dir"))) {
        try {
            val unresolved = toml.decodeFromNativeReader<UnresolvedLocalAgentRegistry>(stream.reader())
            val context = RegistryResolutionContext(
                path = path,
                registrySourceIdentifier = AgentRegistrySourceIdentifier.Local
            )

            addLocalAgents(unresolved.resolve(context), "file:$path")
        } catch (e: SerializationException) {
            logger.error(e) { "Failed to parse agent registry $path" }
        } catch (e: RegistryException) {
            logger.error(e) { "Registry $path contains badly formatted agents" }
        }
    }
}

/**
 * This class represents an entire agent registry available to a Coral server.  Generally, there should exist only one
 * registry per server.  An agent registry can contain multiple sources, however, which can may be added or removed to
 * a (or "the" most of the time) agent registry.
 *
 * Sources can be one of three types:
 * 1. [AgentRegistrySourceIdentifier.Local]
 * 2. [AgentRegistrySourceIdentifier.Marketplace]
 * 3. [AgentRegistrySourceIdentifier.Linked]
 *
 * [AgentRegistrySourceIdentifier.Local] registry types contain local agents that are likely to run on the server itself.  These agents are typically
 * in-house agents or agents that are being actively developed.
 *
 * The [AgentRegistrySourceIdentifier.Marketplace] type represents the Coral marketplace registry.  There should only
 * ever be one of these present, and the presence of this is optional.  This allows for direct consumption of agents
 * from the marketplace.  Resolution of agents from this source involves queries to the Coral marketplace.
 *
 * [AgentRegistrySourceIdentifier.Linked] registry types represent local registries from other Coral servers that are
 * linked to this server.  Linked servers can be used for many reasons, but the primarily immediate reason is so that
 * Coral Cloud users are able to run development agents in their cloud sessions.  Registry sources of this type also
 * involve network queries during resolution.
 */
class AgentRegistry(build: AgentRegistrySourceBuilder.() -> Unit) {
    /**
     * A list of all agent registry sources.  Note this is mutable and can be modified at runtime.  Modification, for
     * example, can occur when new linked servers connect.
     */
    val sources = run {
        val builder = AgentRegistrySourceBuilder()
        builder.build()

        builder.sources
    }

    /**
     * A flattened list of all agents from all sources.  This list may contain duplicates.  Deduplication is only done
     * on local registries, so, for example, duplicates can exist between local and marketplace registries, or between
     * linked server registries and local/marketplace registries.
     */
    val agents = sources.flatMap { it.agents }

    /**
     * A list of all sources where all local sources of type [ListAgentRegistrySource] are merged into a single source.
     */
    val mergedSources = buildList {
        val localAgents = mutableListOf<RegistryAgent>()

        sources.forEach { source ->
            if (source.identifier == AgentRegistrySourceIdentifier.Local && source is ListAgentRegistrySource) {
                localAgents.addAll(source.registryAgents)
            } else {
                add(source)
            }
        }

        add(ListAgentRegistrySource(localAgents))
    }

    /**
     * Returns a list of all exported agents from all local sources.
     *
     * Exported agents are agents sourced in a local registry that have defined export settings.  Exported agents can be
     * rented by other Coral servers, compensated via (currently) Solana-backed payments.  The presence of export
     * settings fully controls an agents' export status.
     */
    suspend fun getExportedAgents(): List<RestrictedRegistryAgent> {
        return sources
            .filter { it.identifier == AgentRegistrySourceIdentifier.Local }
            .flatMap { source ->
                buildList {
                    source.agents.forEach { catalog ->
                        catalog.versions.forEach { version ->
                            val agent =
                                source.resolveAgent(RegistryAgentIdentifier(catalog.name, version, source.identifier))

                            if (agent.registryAgent.exportSettings.isNotEmpty())
                                add(agent)
                        }
                    }
                }
            }
    }

    /**
     * Resolves an agent using a [RegistryAgentIdentifier].  This identifier specifies the name, version, and registry
     * source of the requested agent.  If [id] contains a [AgentRegistrySourceIdentifier.Local] identifier, this
     * function is almost instant.  If [id] uses [AgentRegistrySourceIdentifier.Marketplace] or
     * [AgentRegistrySourceIdentifier.Linked] this function requires network communication and may operate slower.
     *
     * @param id The identifier of the agent to resolve
     *
     * @throws RegistryException.RegistrySourceNotFoundException if the specified registry source does not exist
     * @throws RegistryException.AgentNotFoundException if [id] was not found in the registry source
     */
    suspend fun resolveAgent(id: RegistryAgentIdentifier): RestrictedRegistryAgent {
        val sources = sources
            .filter { it.identifier == id.registrySourceId }

        if (sources.isEmpty())
            throw RegistryException.RegistrySourceNotFoundException("No registry sources for '${id.registrySourceId}' found")

        sources.forEach {
            try {
                return it.resolveAgent(id)
            } catch (_: RegistryException.AgentNotFoundException) {

            }
        }

        throw RegistryException.AgentNotFoundException("Agent '${id.name}' not found in any of the ${sources.size} registry sources matching '${id.registrySourceId}'")
    }
}