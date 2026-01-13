package org.coralprotocol.coralserver.agent.registry

import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.decodeFromNativeReader
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.coralprotocol.coralserver.util.isWindows
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * A toml based agent registry source, matching toml files based on a given pattern.
 *
 * A basic path pattern is the path to a directory that contains a single coral-agent.toml file.  Given the following
 * structure:
 *
 * ```
 * my_agents/
 * ├─ agent1/
 * │  ├─ coral-agent.toml
 * ├─ agent2/
 * │  ├─ coral-agent.toml
 * ```
 *
 * A pattern of "my_agents/agent1" will load the agent that "my_agents/agent1/coral-agent.toml" describes into a
 * local registry source.
 *
 * More advanced patterns containing the `*` character can be used to add multiple agents at once.  Given the above
 * structure again, a pattern of "my_agents/&#42;" will match my_agents/agent1 and my_agents/agent2 including both:
 * - my_agent/agent1/coral-agent.toml
 * - my_agent/agent2/coral-agent.toml
 */
class FileAgentRegistrySource(val pattern: String) : AgentRegistrySource(AgentRegistrySourceIdentifier.Local) {
    val logger by inject<Logger>(named(LOGGER_CONFIG))
    val toml by inject<Toml>()

    private val mutableAgents = buildList {
        findAgentFilesRecursive(pattern).forEach {
            add(
                toml.decodeFromNativeReader<UnresolvedRegistryAgent>(it.reader()).resolve(
                    AgentResolutionContext(
                        registrySourceIdentifier = AgentRegistrySourceIdentifier.Local,
                        path = it.toPath()
                    )
                )
            )
        }
    }.toMutableList()


    fun findAgentFilesRecursive(
        pathPattern: String,
        parent: String? = null,
        root: String? = null,
        files: MutableList<File> = mutableListOf()
    ): List<File> {
        val pathPattern = if (isWindows()) {
            pathPattern.replace("\\", "/")
        } else {
            pathPattern
        }

        val parts = pathPattern.split("/")
        var current = Path.of(parent ?: "")
        parts.forEachIndexed { index, part ->
            if (!current.isDirectory())
                return@forEachIndexed

            if (part == "*") {
                current.toFile().listFiles {
                    it.isDirectory
                }?.forEach {
                    findAgentFilesRecursive(
                        if (index == parts.lastIndex) {
                            it.name
                        } else {
                            "${it.name}/${parts.slice(IntRange(index + 1, parts.size - 1)).joinToString("/")}"
                        },
                        current.toString(),
                        root ?: pathPattern,
                        files
                    )
                }
            } else {
                current = current.resolve(part)
            }
        }

        val fullPotentialFile = current.resolve(AGENT_FILE).toFile()
        if (fullPotentialFile.exists())
            files.add(fullPotentialFile)

        return files.toList()
    }

    override val agents: List<RegistryAgentCatalog>
        get() = buildList {
            buildMap {
                mutableAgents.forEach {
                    getOrPut(it.name, ::mutableListOf).add(it.version)
                }
            }.forEach { (name, versions) ->
                add(RegistryAgentCatalog(name, versions))
            }
        }

    override suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent {
        TODO("")
    }
}