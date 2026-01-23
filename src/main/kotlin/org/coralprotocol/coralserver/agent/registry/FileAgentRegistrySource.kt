@file:OptIn(FlowPreview::class)

package org.coralprotocol.coralserver.agent.registry

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.decodeFromNativeReader
import org.coralprotocol.coralserver.logging.Logger
import org.coralprotocol.coralserver.modules.LOGGER_CONFIG
import org.coralprotocol.coralserver.util.isWindows
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchEvent
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A toml based agent registry source, matching toml files based on a given pattern.
 *
 * ### [pattern]
 * A basic path pattern is the path to a directory that contains a single coral-agent.toml file.
 *
 * Given the following structure:
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
 *
 * Patterns should be absolute paths and should not start with a '*' character.
 *
 * ### [watch]
 *
 * This class has the ability to register watchers on [watchCoroutineScope] to automatically update the registered
 * agents.  This is a useful development utility but has some considerations:
 *
 * 1. This should not be turned on in production.
 * 2. There are limitations on what the JVM allows when it comes to watches.  It is not technically possible to reach
 * 100% coverage with the JVM's [java.nio.file.WatchService] and as a back-up [scan] is provided.  Consider using
 * [scanOnInterval] to provide 100% coverage.
 *
 * Scanning will attempt to find:
 * 1. New agents that match the provided pattern
 * 2. Agents that have been deleted
 * 3. Modifications to existing agents
 *
 * Feature 1. has a chance of missing new agents, especially when the directories involved in the creation of the agent
 * were programmatically created - faster than the [java.nio.file.WatchService] is able to catch.
 */
class FileAgentRegistrySource(
    val registry: AgentRegistry,
    val pattern: String,
    val watch: Boolean = false,
    val watchCoroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    restrictions: Set<RegistryAgentRestriction> = setOf()
) : ListAgentRegistrySource(name = "pattern [${normalizedPathString(pattern)}]", restrictions = restrictions) {

    data class WatchJobKey(
        val path: Path,
        val kinds: List<WatchEvent.Kind<*>>
    )

    private val logger by inject<Logger>(named(LOGGER_CONFIG))
    private val toml by inject<Toml>()
    private val loadedAgentFiles = ConcurrentHashMap.newKeySet<String>()
    private val deletionWatchers = ConcurrentHashMap.newKeySet<String>()
    private val watchJobs = ConcurrentHashMap<WatchJobKey, Job>()

    private var parentPattern: String
    private var remainingPattern: String

    init {
        val parts = normalizedPathString(pattern).split("/")
        parentPattern = if (isWindows()) parts.first() else "/${parts.first()}"
        remainingPattern = parts.slice(1..<parts.size).joinToString("/")

        scan()
    }

    fun scan() {
        loadedAgentFiles.clear()
        deletionWatchers.clear()
        clearAgents()
        watchJobs.forEach { (_, job) -> job.cancel() }
        watchJobs.clear()

        addAgentsFromPattern(remainingPattern, parentPattern)
    }

    fun scanOnInterval(interval: Duration) {
        watchCoroutineScope.launch {
            delay(interval)
            scan()
        }
    }

    private fun addAgentsFromPattern(pathPattern: String, parent: String) {
        val parts = pathPattern.split("/")
        var current = Path.of(parent).absolute()
        parts.forEachIndexed { index, part ->
            val remainingParts = parts.slice(index..<parts.size)

            if (part == "*") {
                logger.info {
                    "watching directory: \"${normalizedPathString(current)}\" for \"${
                        remainingParts.joinToString(
                            "/"
                        )
                    }\""
                }

                // watch this directory for any future items matching the remainder of the pattern (this function will
                // do nothing if watch = false)
                watchDirectory(current, remainingParts)

                current.toFile().listFiles {
                    it.isDirectory
                }?.forEach {
                    addAgentsFromPattern(
                        if (index == parts.lastIndex) {
                            it.name
                        } else {
                            "${it.name}/${parts.slice(index + 1..<parts.size).joinToString("/")}"
                        },
                        normalizedPathString(current)
                    )
                }
            } else {
                current = current.resolve(part)
            }

            if (current.parent != null && (index != parts.lastIndex || part == "*")) {
                if (!current.isDirectory() && current.parent.isDirectory()) {
                    logger.info { "watching directory: \"${normalizedPathString(current.parent)}\" for \"$part\"" }
                    watchDirectory(current.parent, remainingParts)
                    return
                } else {
                    watchForDeletion(
                        current,
                        parts.slice(index..<parts.size).joinToString("/"),
                        normalizedPathString(current.parent)
                    )
                }
            }
        }

        // if the last part in this pattern is a wildcard, directories are expected here not agents
        if (parts.last() == "*")
            return

        val agentFile = current.resolve(AGENT_FILE).toFile()
        if (agentFile.exists()) {
            addAgentFromFile(agentFile)
        } else {

            // watching allows for us to wait for agent to be written to this directory
            waitForAgent(current)
            watchForDeletion(current, parent, parts.last())
        }
    }

    private fun addAgentFromFile(agentFile: File) {
        /*
            There is a possible circumstance where a file is attempted to be loaded twice (especially) when agent files
            are programmatically written.  I have observed (on Windows) the following:

            1. A pattern is given that has no parts currently created, e.g "agents / * "
            2. Programmatically, "agents/agent1/coral-agent.toml" is written (all directories and the file)
            3. The watcher waiting for the "agents" directory to be created calls addAgentsFromPattern.  Because the
               pattern that matched "agents" is "*", a watcher is installed in "agents" to monitor for further
               directories.  addAgentsFromPattern will also immediately traverse the directory and find the full file
               "agents/agent1/coral-agent.toml" - adding it with a call to this function
            4. The installed watched from step 3. also reports that "agents/agent1/coral-agent.toml" was just created
               and calls this function again for the same file
         */
        try {
            val absolutePath = agentFile.absolutePath
            if (loadedAgentFiles.contains(absolutePath))
                return

            loadedAgentFiles.add(absolutePath)

            val agent = readAgent(agentFile)
            if (agentCache.containsKey(agent.identifier)) {
                logger.warn { "cannot add agent from file \"${normalizedPathString(agentFile)}\" because the identifier \"${agent.identifier}\" is already taken" }

                // can still watch this agent though
                watchSingleAgent(agentFile, null)
                return
            }

            addAgent(agent)
            watchSingleAgent(agentFile, agent)

            logger.info { "agent added: ${agent.identifier} - ${normalizedPathString(agentFile)}" }
        } catch (e: Exception) {
            watchSingleAgent(agentFile, null)
            logger.error(e) { "Error loading agent from file ${normalizedPathString(agentFile)}" }
        }
    }

    private fun eventStreamForPath(
        path: Path,
        vararg kinds: WatchEvent.Kind<*>,
        handler: suspend CoroutineScope.(WatchEvent<*>) -> Unit
    ): Job {
        val watchJobKey = WatchJobKey(path, kinds.toList())
        if (watchJobs.containsKey(watchJobKey)) {
            watchJobs[watchJobKey]?.cancel()
        }

        val watchService = FileSystems.getDefault().newWatchService()
        path.register(watchService, *kinds)

        return watchCoroutineScope.launch {
            while (true) {
                try {
                    val key = runInterruptible { watchService.take() }
                    for (event in key.pollEvents()) {
                        handler(event)
                    }

                    if (!key.reset())
                        break
                } catch (e: InterruptedException) {
                    throw e
                } catch (_: NoSuchFileException) {
                    break
                } catch (_: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.error(e) { "Error watching path \"${normalizedPathString(path)}\"" }
                }
            }
        }.apply {
            watchJobs[watchJobKey] = this
            invokeOnCompletion {
                watchJobs.remove(watchJobKey)
                watchService.close()
            }
        }
    }

    private fun watchSingleAgent(agentFile: File, agent: RegistryAgent?) {
        var agent = agent
        if (!watch)
            return

        val watchPath = agentFile.toPath().parent
        if (!watchPath.isDirectory()) {
            logger.warn { "cannot watch non-existent directory \"${normalizedPathString(watchPath)}\"!" }
            return
        }

        val modificationFlow = MutableSharedFlow<File>(extraBufferCapacity = 64)
        val flowJob = modificationFlow
            .debounce(500.milliseconds)
            .onEach { agentFile ->
                try {
                    val newAgent = readAgent(agentFile)
                    when (val agent = agent) {
                        newAgent -> {
                            logger.info {
                                "agent file updated but parsed contents did not change - \"${
                                    normalizedPathString(
                                        agentFile
                                    )
                                }\""
                            }
                        }

                        null -> {
                            if (agentCache.containsKey(newAgent.identifier)) {
                                logger.warn { "cannot add agent from file \"${normalizedPathString(agentFile)}\" because the identifier \"${newAgent.identifier}\" is already taken" }
                                return@onEach
                            }

                            addAgent(newAgent)
                            logger.info { "agent added: ${newAgent.identifier} - \"${normalizedPathString(agentFile)}\"" }
                        }

                        else -> {
                            if (agentCache.containsKey(newAgent.identifier)) {
                                logger.warn { "cannot update agent from file \"${normalizedPathString(agentFile)}\" because the new identifier \"${newAgent.identifier}\" is already taken" }
                                return@onEach
                            }

                            removeAgent(agent)

                            val identifier = if (newAgent.identifier != agent.identifier) {
                                "${agent.identifier} (new identifier: ${newAgent.identifier})"
                            } else {
                                agent.identifier.toString()
                            }

                            addAgent(newAgent)

                            if (newAgent != agent) {
                                logger.info { "agent $identifier updated" }
                                registry.reportLocalDuplicates()
                            }
                        }
                    }

                    agent = newAgent
                } catch (e: Exception) {
                    logger.error(e) { "Error parsing new contents for agent file \"${normalizedPathString(agentFile)}\"" }
                }
            }
            .launchIn(watchCoroutineScope)

        eventStreamForPath(watchPath, ENTRY_MODIFY, ENTRY_DELETE) {
            val fileName = it.context() as Path
            if (fileName.name != agentFile.name)
                return@eventStreamForPath

            when (it.kind()) {
                ENTRY_MODIFY -> {
                    modificationFlow.tryEmit(agentFile)
                }

                ENTRY_DELETE -> {
                    if (agent != null) {
                        logger.warn { "agent deleted: ${agent.identifier} - \"${normalizedPathString(agentFile)}\"" }
                        loadedAgentFiles.remove(agentFile.absolutePath)
                        removeAgent(agent)

                        // if the user deletes and re-adds an agent, it will need this watcher
                        waitForAgent(agentFile.toPath().parent)

                        cancel()
                    }
                }
            }
        }.invokeOnCompletion {
            flowJob.cancel()
            if (agent != null)
                logger.info { "watcher for agent ${agent.identifier} - \"${normalizedPathString(agentFile)}\" stopped" }
        }
    }

    private fun watchDirectory(directory: Path, remainingParts: List<String>) {
        if (!watch)
            return

        if (!directory.isDirectory()) {
            logger.warn { "cannot watch non-existent directory \"${normalizedPathString(directory)}\"!" }
            return
        }

        val nextPart = remainingParts.first()
        val remainingStr = remainingParts.joinToString("/")

        eventStreamForPath(directory, ENTRY_CREATE) {
            val fileName = (it.context() as Path).name
            val wildcard = nextPart == "*"
            if (nextPart.equals(fileName, ignoreCase = isWindows()) || wildcard) {
                val fullPatternLog = if (nextPart != remainingStr) {
                    " from full pattern \"$remainingStr\""
                } else {
                    ""
                }

                logger.info { "\"$fileName\" created in \"${normalizedPathString(directory)}\", matching pattern part \"$nextPart\"$fullPatternLog" }

                addAgentsFromPattern(
                    if (wildcard) {
                        if (remainingParts.size == 1) {
                            fileName
                        } else {
                            "$fileName/${remainingParts.drop(1).joinToString("/")}"
                        }
                    } else {
                        remainingParts.joinToString("/")
                    },
                    normalizedPathString(directory)
                )

                // if the next part was a specific directory, and it was created, this listener doesn't need to exist anymore
                if (!wildcard)
                    cancel()
            }
        }.invokeOnCompletion {
            logger.info { "watcher for \"${remainingParts.joinToString("/")}\" in \"${normalizedPathString(directory)}\" stopped" }
        }
    }

    private fun waitForAgent(directory: Path) {
        if (!watch)
            return

        if (!directory.isDirectory()) {
            logger.warn { "cannot watch non-existent directory \"${normalizedPathString(directory)}\"!" }
            return
        }

        logger.info { "waiting for $AGENT_FILE to be written in \"${normalizedPathString(directory)}\"" }

        eventStreamForPath(directory, ENTRY_CREATE) {
            if ((it.context() as Path).name == AGENT_FILE) {
                val file = directory.resolve(AGENT_FILE).toFile()

                // Allow time for contents to be written to this file
                delay(500.milliseconds)

                // If the file still exists, check for contents, if no contents, wait for modification instead of
                // immediately throwing an error
                if (file.exists()) {
                    if (file.toPath().fileSize() == 0L) {
                        watchSingleAgent(file, null)
                    } else
                        addAgentFromFile(directory.resolve(AGENT_FILE).toFile())
                }

                cancel()
            }
        }.invokeOnCompletion {
            logger.info { "watcher for $AGENT_FILE in \"${normalizedPathString(directory)}\" stopped" }
        }
    }

    private fun watchForDeletion(directory: Path, restartPathPattern: String, restartPart: String) {
        if (!watch || directory.parent == null || deletionWatchers.contains(directory.absolutePathString()))
            return

        if (!directory.isDirectory()) {
            logger.warn { "cannot watch non-existent directory \"${normalizedPathString(directory)}\"!" }
            return
        }

        deletionWatchers.add(directory.absolutePathString())
        eventStreamForPath(directory.parent, ENTRY_DELETE) {
            if ((it.context() as Path).name == directory.name) {
                logger.info { "${directory.name} in \"${normalizedPathString(directory.parent)}\" was deleted, restart $restartPathPattern with $restartPart" }

                deletionWatchers.remove(directory.absolutePathString())
                addAgentsFromPattern(restartPathPattern, restartPart)

                cancel()
            }
        }
    }

    private fun readAgent(agentFile: File) =
        toml.decodeFromNativeReader<UnresolvedRegistryAgent>(agentFile.reader()).resolve(
            AgentResolutionContext(
                registrySourceIdentifier = AgentRegistrySourceIdentifier.Local,
                path = agentFile.parentFile.toPath()
            )
        )
}

private fun normalizedPathString(path: String) =
    if (isWindows()) {
        path.replace("\\", "/")
    } else {
        path
    }

private fun normalizedPathString(file: File) =
    normalizedPathString(file.absolutePath)

private fun normalizedPathString(path: Path) =
    normalizedPathString(path.toString())