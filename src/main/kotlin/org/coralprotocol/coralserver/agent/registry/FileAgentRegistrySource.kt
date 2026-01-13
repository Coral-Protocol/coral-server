package org.coralprotocol.coralserver.agent.registry

import kotlinx.coroutines.*
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.decodeFromNativeReader
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.coralprotocol.coralserver.util.isWindows
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchEvent
import kotlin.io.path.isDirectory
import kotlin.io.path.name

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
class FileAgentRegistrySource(
    pattern: String,
    val watch: Boolean = false,
    val watchCoroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : AgentRegistrySource(AgentRegistrySourceIdentifier.Local) {

    val logger by inject<Logger>(named(LOGGER_CONFIG))
    val toml by inject<Toml>()

    private val mutableAgents = mutableListOf<RegistryAgent>()

    init {
        addAgentsFromPattern(pattern)
    }

    fun addAgentsFromPattern(pathPattern: String, parent: String? = null, root: String? = null) {
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
                    addAgentsFromPattern(
                        if (index == parts.lastIndex) {
                            it.name
                        } else {
                            "${it.name}/${parts.slice(IntRange(index + 1, parts.size - 1)).joinToString("/")}"
                        },
                        current.toString(),
                        root ?: pathPattern
                    )
                }
            } else {
                current = current.resolve(part)
            }
        }

        val fullPotentialFile = current.resolve(AGENT_FILE).toFile()
        if (fullPotentialFile.exists()) {
            val agent = readAgent(fullPotentialFile)
            mutableAgents.add(agent)
            watchSingleAgent(fullPotentialFile, agent)

            logger.info { "agent added: ${agent.identifier} - $fullPotentialFile" }
        }
    }

    private fun eventStreamForPath(path: Path, handler: CoroutineScope.(WatchEvent<*>) -> Unit): Job {
        val watchService = FileSystems.getDefault().newWatchService()
        path.register(watchService, ENTRY_MODIFY, ENTRY_DELETE, ENTRY_CREATE)

        return watchCoroutineScope.launch {
            while (true) {
                val key = watchService.take()

                for (event in key.pollEvents()) {
                    handler(event)
                }

                key.reset()
            }
        }
    }

    fun watchSingleAgent(agentFile: File, agent: RegistryAgent) {
        if (!watch)
            return

        val watchService = FileSystems.getDefault().newWatchService()
        val path = agentFile.toPath().parent
        path.register(watchService, ENTRY_MODIFY, ENTRY_DELETE)

        eventStreamForPath(agentFile.toPath().parent) {
            val fileName = it.context() as Path
            if (fileName.name != agentFile.name)
                return@eventStreamForPath

            when (it.kind()) {
                ENTRY_MODIFY, ENTRY_CREATE -> {
                    try {
                        mutableAgents[mutableAgents.indexOf(agent)] = readAgent(agentFile)
                        logger.info { "agent updated: ${agent.identifier} - $agentFile" }
                    } catch (e: Exception) {
                        logger.error(e) { "Error reading new contents for agent $agent provided by $agentFile" }
                    }
                }

                ENTRY_DELETE -> {
                    logger.warn { "agent deleted: ${agent.identifier} - $agentFile" }
                    mutableAgents.remove(agent)
                    cancel()
                }
            }
        }
    }

    fun watchDirectory() {

    }

    private fun readAgent(agentFile: File) =
        toml.decodeFromNativeReader<UnresolvedRegistryAgent>(agentFile.reader()).resolve(
            AgentResolutionContext(
                registrySourceIdentifier = AgentRegistrySourceIdentifier.Local,
                path = agentFile.toPath()
            )
        )

    override val agents: List<RegistryAgentCatalog>
        get() = ListAgentRegistrySource(mutableAgents).agents

    override suspend fun resolveAgent(agent: RegistryAgentIdentifier): RestrictedRegistryAgent =
        ListAgentRegistrySource(mutableAgents).resolveAgent(agent)
}